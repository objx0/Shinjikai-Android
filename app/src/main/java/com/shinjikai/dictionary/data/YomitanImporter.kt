package com.shinjikai.dictionary.data

import androidx.room.withTransaction
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val YOMITAN_DEFINITION_MULTISPACE_REGEX = Regex("""\s{2,}""")

class YomitanImporter(
    private val database: AppDatabase
) {
    private val yomitanDao = database.yomitanDao()

    suspend fun importFromZip(zipStream: InputStream, sourceLabel: String = "yomitan-v2"): Result<Int> {
        return runCatching {
            withContext(Dispatchers.IO) {
                AppDatabase.repairOfflineDictionarySchema(database.openHelper.writableDatabase)

                var importedCount = 0
                var idSeed = -1
                val buffer = ArrayList<YomitanTermEntity>(1200)

                coroutineContext.ensureActive()
                database.withTransaction {
                    replaceSource(sourceLabel)
                    idSeed = yomitanDao.nextNegativeTermId()

                    ZipInputStream(BufferedInputStream(zipStream)).use { zis ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val entry = zis.nextEntry ?: break
                            val isTermBank = entry.name.lowercase(Locale.ROOT).startsWith("term_bank_")
                            if (!isTermBank || !entry.name.endsWith(".json")) {
                                zis.closeEntry()
                                continue
                            }

                            val reader = JsonReader(InputStreamReader(zis, Charsets.UTF_8)).apply {
                                isLenient = true
                            }

                            parseTermBank(reader) { expression, reading, glossary ->
                                coroutineContext.ensureActive()
                                if (expression.isBlank() && reading.isBlank()) return@parseTermBank

                                buffer.add(
                                    YomitanTermEntity(
                                        id = idSeed,
                                        expression = expression.ifBlank { reading },
                                        reading = reading.ifBlank { expression },
                                        glossary = glossary.ifBlank { "-" },
                                        difficulty = 0,
                                        note = "",
                                        source = sourceLabel,
                                        detailsJson = null
                                    )
                                )
                                idSeed -= 1
                                importedCount += 1

                                if (buffer.size >= 1200) {
                                    yomitanDao.upsertAll(buffer)
                                    yomitanDao.upsertAllFts(buffer.map { it.toFts() })
                                    buffer.clear()
                                }
                            }

                            zis.closeEntry()
                        }
                    }

                    if (buffer.isNotEmpty()) {
                        yomitanDao.upsertAll(buffer)
                        yomitanDao.upsertAllFts(buffer.map { it.toFts() })
                        buffer.clear()
                    }

                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_source", value = sourceLabel))
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_count", value = importedCount.toString()))
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

    suspend fun importFromDirectory(directory: File, sourceLabel: String = "yomitan-v2"): Result<Int> {
        return runCatching {
            withContext(Dispatchers.IO) {
                require(directory.exists()) { "Offline source directory was not found." }
                require(directory.isDirectory) { "Offline source path is not a directory." }

                AppDatabase.repairOfflineDictionarySchema(database.openHelper.writableDatabase)

                val termBankFiles = directory.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.name.lowercase(Locale.ROOT).startsWith("term_bank_") &&
                            file.extension.equals("json", ignoreCase = true)
                    }
                    .sortedBy { it.absolutePath }
                    .toList()

                require(termBankFiles.isNotEmpty()) { "Selected archive does not contain Yomitan term banks." }

                var importedCount = 0
                var idSeed = -1
                val buffer = ArrayList<YomitanTermEntity>(1200)

                coroutineContext.ensureActive()
                database.withTransaction {
                    replaceSource(sourceLabel)
                    idSeed = yomitanDao.nextNegativeTermId()

                    termBankFiles.forEach { file ->
                        coroutineContext.ensureActive()
                        val reader = JsonReader(file.reader(Charsets.UTF_8)).apply {
                            isLenient = true
                        }

                        reader.use {
                            parseTermBank(it) { expression, reading, glossary ->
                                coroutineContext.ensureActive()
                                if (expression.isBlank() && reading.isBlank()) return@parseTermBank

                                buffer.add(
                                    YomitanTermEntity(
                                        id = idSeed,
                                        expression = expression.ifBlank { reading },
                                        reading = reading.ifBlank { expression },
                                        glossary = glossary.ifBlank { "-" },
                                        difficulty = 0,
                                        note = "",
                                        source = sourceLabel,
                                        detailsJson = null
                                    )
                                )
                                idSeed -= 1
                                importedCount += 1

                                if (buffer.size >= 1200) {
                                    yomitanDao.upsertAll(buffer)
                                    yomitanDao.upsertAllFts(buffer.map { it.toFts() })
                                    buffer.clear()
                                }
                            }
                        }
                    }

                    if (buffer.isNotEmpty()) {
                        yomitanDao.upsertAll(buffer)
                        yomitanDao.upsertAllFts(buffer.map { it.toFts() })
                        buffer.clear()
                    }

                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_source", value = sourceLabel))
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_count", value = importedCount.toString()))
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

    private suspend fun replaceSource(sourceLabel: String) {
        yomitanDao.deleteCategoryRefsForSource(sourceLabel)
        yomitanDao.deleteTermsFtsForSource(sourceLabel)
        yomitanDao.deleteTermsForSource(sourceLabel)
    }

    private suspend fun parseTermBank(
        reader: JsonReader,
        onRow: suspend (expression: String, reading: String, glossary: String) -> Unit
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }

        reader.beginArray()
        while (reader.hasNext()) {
            coroutineContext.ensureActive()
            parseTermRow(reader, onRow)
        }
        reader.endArray()
    }

    private suspend fun parseTermRow(
        reader: JsonReader,
        onRow: suspend (expression: String, reading: String, glossary: String) -> Unit
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }

        reader.beginArray()
        var index = 0
        var expression = ""
        var reading = ""
        var glossary = ""
        while (reader.hasNext()) {
            coroutineContext.ensureActive()
            when (index) {
                0 -> expression = readScalarString(reader).trim()
                1 -> reading = readScalarString(reader).trim()
                5 -> glossary = readDefinitionText(reader)
                else -> reader.skipValue()
            }
            index += 1
        }
        reader.endArray()

        onRow(expression, reading, glossary)
    }

    private fun readScalarString(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private suspend fun readDefinitionText(reader: JsonReader): String {
        val parts = ArrayList<String>(8)
        readAnyText(reader, parts)
        return parts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .replace(YOMITAN_DEFINITION_MULTISPACE_REGEX, " ")
            .trim()
    }

    private suspend fun readAnyText(reader: JsonReader, sink: MutableList<String>) {
        when (reader.peek()) {
            JsonToken.STRING -> sink.add(reader.nextString())
            JsonToken.NUMBER -> sink.add(reader.nextString())
            JsonToken.BOOLEAN -> sink.add(reader.nextBoolean().toString())
            JsonToken.NULL -> reader.nextNull()
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    coroutineContext.ensureActive()
                    readAnyText(reader, sink)
                }
                reader.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val content = ArrayList<String>(4)
                val other = ArrayList<String>(4)
                while (reader.hasNext()) {
                    coroutineContext.ensureActive()
                    val name = reader.nextName()
                    if (name == "content") {
                        readAnyText(reader, content)
                    } else {
                        readAnyText(reader, other)
                    }
                }
                reader.endObject()
                val chosen = if (content.any { it.isNotBlank() }) content else other
                sink.addAll(chosen)
            }
            else -> reader.skipValue()
        }
    }

    private fun YomitanTermEntity.toFts(): YomitanTermFtsEntity {
        return YomitanTermFtsEntity(
            rowId = id,
            expression = expression,
            reading = reading,
            glossary = glossary
        )
    }
}
