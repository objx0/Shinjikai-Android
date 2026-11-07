package com.shinjikai.dictionary.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val RAW_SHINJIKAI_WHITESPACE_REGEX = Regex("""\s+""")

internal data class JsonLineInput(
    val label: String,
    val openStream: () -> InputStream
)

class RawShinjikaiImporter(
    private val database: AppDatabase,
    private val gson: Gson = Gson()
) {
    private val yomitanDao = database.yomitanDao()

    suspend fun importFromJsonLines(
        jsonlFile: File,
        sourceLabel: String = "raw-shinjikai-jsonl"
    ): Result<Int> {
        require(jsonlFile.exists()) { "Offline source file was not found." }
        require(jsonlFile.isFile) { "Offline source path is not a file." }

        return importFromJsonLineStreams(
            sources = listOf(JsonLineInput(jsonlFile.name) { jsonlFile.inputStream() }),
            sourceLabel = sourceLabel
        )
    }

    internal suspend fun importFromJsonLineFiles(
        jsonlFiles: List<File>,
        sourceLabel: String = "raw-shinjikai-jsonl"
    ): Result<Int> {
        require(jsonlFiles.isNotEmpty()) { "Offline source files were not found." }
        jsonlFiles.forEach { file ->
            require(file.exists()) { "Offline source file was not found: ${file.name}" }
            require(file.isFile) { "Offline source path is not a file: ${file.name}" }
        }

        return importFromJsonLineStreams(
            sources = jsonlFiles.map { file -> JsonLineInput(file.name) { file.inputStream() } },
            sourceLabel = sourceLabel
        )
    }

    internal suspend fun importFromJsonLineStreams(
        sources: List<JsonLineInput>,
        sourceLabel: String = "raw-shinjikai-jsonl",
        offlineImageRoot: String? = null
    ): Result<Int> {
        return runCatching {
            withContext(Dispatchers.IO) {
                require(sources.isNotEmpty()) { "Offline source files were not found." }

                AppDatabase.repairOfflineDictionarySchema(database.openHelper.writableDatabase)

                var importedCount = 0
                val categoriesById = linkedMapOf<Int, CategoryRef>()
                val buffer = ArrayList<YomitanTermEntity>(500)
                val categoryBuffer = ArrayList<YomitanTermCategoryEntity>(1000)

                database.withTransaction {
                    replaceSource(sourceLabel)
                    var nextNegativeId = yomitanDao.nextNegativeTermId()

                    sources.forEach { source ->
                        source.openStream().bufferedReader(Charsets.UTF_8).use { reader ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val rawLine = reader.readLine() ?: break
                                val line = rawLine.trim()
                                if (line.isEmpty()) continue

                                val record = gson.fromJson(line, RawShinjikaiRecord::class.java) ?: continue
                                val word = record.word ?: continue
                                val id = if (sourceLabel == BundledDictionaryInstaller.BUNDLED_SOURCE_LABEL) {
                                    word.id
                                } else {
                                    nextNegativeId--
                                }
                                if (id == 0) continue

                                val normalizedWord = word.copy(
                                    pictures = word.pictures.orEmpty()
                                        .mapNotNull(::normalizeStoredPictureElement),
                                    meanings = word.meanings.map { meaning ->
                                        meaning.copy(
                                            pictures = meaning.pictures.mapNotNull(::normalizeStoredPictureElement)
                                        )
                                    }
                                )
                                val expression = normalizedWord.writings.firstOrNull()
                                    ?.text
                                    ?.trim()
                                    .orEmpty()
                                    .ifBlank { normalizedWord.kana.trim() }
                                val reading = normalizedWord.kana.trim().ifBlank { expression }
                                if (!hasDictionaryHeadword(expression, reading)) continue

                                record.categories.forEach { category ->
                                    if (category.id > 0 && category.name.isNotBlank()) {
                                        categoriesById.putIfAbsent(category.id, category)
                                        categoryBuffer.add(
                                            YomitanTermCategoryEntity(
                                                termId = id,
                                                categoryId = category.id
                                            )
                                        )
                                    }
                                }

                                val details = WordDetailsResponse(
                                    word = normalizedWord,
                                    similarWords = record.similarWords.map {
                                        WordRef(id = it.id, kana = it.kana, writings = it.writings)
                                    },
                                    sentenceSearch = record.sentenceSearch,
                                    sentenceMap = record.sentenceMap,
                                    homophones = record.homophones.map {
                                        WordRef(id = it.id, kana = it.kana, writings = it.writings)
                                    },
                                    kanjis = record.kanjis
                                )

                                buffer.add(
                                    YomitanTermEntity(
                                        id = id,
                                        expression = expression,
                                        reading = reading,
                                        glossary = buildMeaningSummary(normalizedWord.meanings),
                                        difficulty = normalizedWord.difficulty,
                                        note = normalizedWord.meanings.firstOrNull()?.note.orEmpty().trim(),
                                        source = sourceLabel,
                                        detailsJson = gson.toJson(details),
                                        jlpt = normalizedWord.jlpt,
                                        searchText = ArabicSearch.buildSearchText(
                                            normalizedWord.meanings.map { it.arabic.orEmpty() }
                                        )
                                    )
                                )
                                importedCount += 1

                                if (buffer.size >= 500) {
                                    flush(buffer, categoryBuffer)
                                }
                            }
                        }
                    }

                    if (buffer.isNotEmpty()) {
                        flush(buffer, categoryBuffer)
                    } else if (categoryBuffer.isNotEmpty()) {
                        yomitanDao.upsertCategoryRefs(categoryBuffer)
                        categoryBuffer.clear()
                    }

                    yomitanDao.upsertMeta(
                        YomitanMetaEntity(
                            key = "categories_json",
                            value = gson.toJson(categoriesById.values.toList())
                        )
                    )
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_source", value = sourceLabel))
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_count", value = importedCount.toString()))
                    offlineImageRoot?.takeIf { it.isNotBlank() }?.let { imageRoot ->
                        yomitanDao.upsertMeta(
                            YomitanMetaEntity(
                                key = OFFLINE_IMAGE_DIR_META_KEY,
                                value = imageRoot
                            )
                        )
                    }
                    yomitanDao.upsertMeta(
                        YomitanMetaEntity(
                            key = "last_import_epoch_ms",
                            value = System.currentTimeMillis().toString()
                        )
                    )
                }

                importedCount
            }
        }
    }

    private suspend fun flush(
        buffer: MutableList<YomitanTermEntity>,
        categoryBuffer: MutableList<YomitanTermCategoryEntity>
    ) {
        yomitanDao.upsertAll(buffer)
        yomitanDao.upsertAllFts(buffer.map { it.toFts() })
        if (categoryBuffer.isNotEmpty()) {
            yomitanDao.upsertCategoryRefs(categoryBuffer)
            categoryBuffer.clear()
        }
        buffer.clear()
    }

    private suspend fun replaceSource(sourceLabel: String) {
        yomitanDao.deleteCategoryRefsForSource(sourceLabel)
        yomitanDao.deleteTermsFtsForSource(sourceLabel)
        yomitanDao.deleteTermsForSource(sourceLabel)
    }

    private fun YomitanTermEntity.toFts(): YomitanTermFtsEntity {
        return YomitanTermFtsEntity(
            rowId = id,
            expression = expression,
            reading = reading,
            glossary = searchText.ifBlank { glossary }
        )
    }

    private fun buildMeaningSummary(meanings: List<Meaning>): String {
        return meanings
            .asSequence()
            .map { it.arabic.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(separator = " / ")
            .replace(RAW_SHINJIKAI_WHITESPACE_REGEX, " ")
            .trim()
    }
}

internal fun hasDictionaryHeadword(expression: String, reading: String): Boolean {
    return expression.isNotBlank() || reading.isNotBlank()
}

private data class RawShinjikaiRecord(
    @SerializedName("Word") val word: WordDetailsWord? = null,
    @SerializedName("Categories") val categories: List<CategoryRef> = emptyList(),
    @SerializedName("SimilarWords") val similarWords: List<SearchItem> = emptyList(),
    @SerializedName("SentenceSearch") val sentenceSearch: List<SentenceExample> = emptyList(),
    @SerializedName("SentenceMap") val sentenceMap: Map<String, SentenceExample> = emptyMap(),
    @SerializedName("Homophones") val homophones: List<SearchItem> = emptyList(),
    @SerializedName("Kanjis") val kanjis: List<KanjiInfo> = emptyList()
)
