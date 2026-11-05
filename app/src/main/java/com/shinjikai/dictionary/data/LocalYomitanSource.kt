package com.shinjikai.dictionary.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private val MULTISPACE_REGEX = Regex("""\s{2,}""")
private val ARABIC_DIACRITICS_REGEX = Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED]""")
private val ARABIC_ALEF_VARIANTS_REGEX = Regex("""[أإآٱ]""")
private val NON_LETTER_NUMBER_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]""")
private val SENTENCE_SPLIT_REGEX = Regex("""[\s\u3000,،;；、。.!?！？()\[\]{}<>\"“”'‘’/\\]+""")
private val FTS_TOKEN_SPLIT_REGEX = Regex("""\s+""")
private val FTS_TOKEN_RESERVED_CHARS_REGEX = Regex("""[\*\^:\(\)\[\]{}!\\|&<>~]""")
private val JAPANESE_PARTICLES = listOf("から", "まで", "より", "だけ", "ほど", "など", "って", "では", "には", "は", "が", "を", "に", "で", "と", "へ", "も", "や", "か", "の")
private const val DETAIL_CACHE_SIZE = 64
private const val SEARCH_PLAN_CACHE_SIZE = 64
private const val FTS_CANDIDATE_POOL = 1500

private data class DetailCacheKey(
    val id: Int,
    val detailsHash: Int,
    val imageRoot: String
)

private data class JapaneseSearchPlan(
    val variantsBySegment: Map<String, List<String>>,
    val searchCandidates: List<String>,
    val ftsQueries: List<String>
) {
    companion object {
        val Empty = JapaneseSearchPlan(
            variantsBySegment = emptyMap(),
            searchCandidates = emptyList(),
            ftsQueries = emptyList()
        )
    }
}

private suspend inline fun <T> runIoCatching(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (throwable: CancellationException) {
        throw throwable
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}

class LocalYomitanSource(
    private val yomitanDao: YomitanDao,
    private val gson: Gson = Gson()
) : DictionarySource {
    private val detailCache = object : LinkedHashMap<DetailCacheKey, WordDetailsResponse>(DETAIL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DetailCacheKey, WordDetailsResponse>?): Boolean {
            return size > DETAIL_CACHE_SIZE
        }
    }
    private val searchPlanCache = object : LinkedHashMap<String, JapaneseSearchPlan>(SEARCH_PLAN_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JapaneseSearchPlan>?): Boolean {
            return size > SEARCH_PLAN_CACHE_SIZE
        }
    }
    private val categoriesCacheLock = Any()
    private var cachedCategories: LoadCategoriesResponse? = null

    override suspend fun searchWords(term: String, page: Int): Result<SearchWordsResponse> {
        val query = term.trim()
        if (query.isBlank()) return Result.success(SearchWordsResponse(items = emptyList()))
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = 80
        val offset = pageIndex * pageSize

        return runIoCatching {
            val isArabic = isArabicQuery(query)
            val japaneseSearchPlan = if (!isArabic) getJapaneseSearchPlan(query) else JapaneseSearchPlan.Empty
            val japaneseVariantsBySegment = japaneseSearchPlan.variantsBySegment
            val japaneseSearchCandidates = japaneseSearchPlan.searchCandidates
            val ftsQueries = japaneseSearchPlan.ftsQueries

            val allRows: List<YomitanTermListRow>
            val pagedRowsDirect: List<YomitanTermListRow>?
            val totalCountDirect: Int?

            if (isArabic) {
                val normalizedQuery = normalizeArabic(query)
                val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

                val ftsCandidates = buildGlossaryOnlyFtsQuery(query)
                    ?.let { glossaryFtsQuery ->
                        yomitanDao.searchGlossaryFts(glossaryFtsQuery, limit = 2500)
                    }
                    .orEmpty()

                val baseRows = if (ftsCandidates.isNotEmpty()) {
                    ftsCandidates
                } else {
                    yomitanDao.searchArabic(
                        term = query,
                        normalizedTerm = normalizedQuery
                    )
                }

                allRows = baseRows
                    .asSequence()
                    .map { row ->
                        val normalizedGlossary = normalizeArabic(row.glossary)
                        val score = scoreArabicMatch(
                            query = normalizedQuery,
                            glossary = normalizedGlossary,
                            tokens = tokens
                        )
                        Triple(row, normalizedGlossary, score)
                    }
                    .filter { (_, glossary, score) ->
                        score < Int.MAX_VALUE && glossary.isNotBlank()
                    }
                    .sortedWith(
                        compareBy<Triple<YomitanTermListRow, String, Int>> { it.third }
                            .thenBy { it.second.length }
                            .thenBy { it.first.id }
                    )
                    .map { it.first }
                    .distinctBy { row ->
                        "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                    }
                    .toList()
                pagedRowsDirect = null
                totalCountDirect = null
            } else if (ftsQueries.isNotEmpty()) {
                // Fixed pool size: a page-dependent pool re-ranks rows between pages,
                // which duplicates/skips items and crashes LazyColumn on repeated keys.
                val perQueryLimit = (FTS_CANDIDATE_POOL / ftsQueries.size).coerceIn(pageSize, FTS_CANDIDATE_POOL)
                val ftsRows = ftsQueries
                    .flatMap { ftsQuery ->
                        yomitanDao.searchFts(
                            matchQuery = ftsQuery,
                            limit = perQueryLimit
                        )
                    }
                allRows = ftsRows
                    .sortedWith(
                        compareByDescending<YomitanTermListRow> {
                            scoreJapaneseSegmentMatches(japaneseVariantsBySegment.values, it)
                        }
                            .thenBy { rankJapaneseQuery(japaneseSearchCandidates, it) }
                            .thenBy { it.id }
                    )
                    .distinctBy { row ->
                        "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                    }
                    .toList()
                pagedRowsDirect = null
                totalCountDirect = null
            } else {
                pagedRowsDirect = yomitanDao.searchPaged(
                    term = query,
                    prefix = "${query}%",
                    limit = pageSize,
                    offset = offset
                ).distinctBy { row ->
                    "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                }
                totalCountDirect = yomitanDao.countSearchMatches(query)
                allRows = emptyList()
            }

            val totalCount = totalCountDirect ?: allRows.size
            val pageCount = if (totalCount == 0) 0 else ((totalCount + pageSize - 1) / pageSize)
            val pageRows = pagedRowsDirect ?: allRows.drop(offset).take(pageSize)

            SearchWordsResponse(
                items = pageRows.map(::toSearchItem),
                page = pageIndex,
                pageCount = pageCount,
                totalCount = totalCount
            )
        }
    }

    override suspend fun loadWordDetails(id: Int): Result<WordDetailsResponse> {
        return runIoCatching {
            val row = yomitanDao.getById(id)
                ?: error("No local entry found for id=$id")
            val imageRoot = yomitanDao.getMetaValue(OFFLINE_IMAGE_DIR_META_KEY)
                ?.takeIf { it.isNotBlank() }
            val cacheKey = DetailCacheKey(
                id = row.id,
                detailsHash = row.detailsJson?.hashCode() ?: 0,
                imageRoot = imageRoot.orEmpty()
            )
            getCachedDetails(cacheKey)?.let { return@runIoCatching it }

            val response = row.detailsJson
                ?.takeIf { it.isNotBlank() }
                ?.let { serialized ->
                    gson.fromJson(serialized, WordDetailsResponse::class.java)
                        .withResolvedOfflineImages(imageRoot)
                        .withResolvedSentenceWordLinks()
                }
                ?: WordDetailsResponse(
                    word = WordDetailsWord(
                        id = row.id,
                        kana = row.reading,
                        writings = listOf(Writing(text = row.expression)),
                        meanings = listOf(
                            Meaning(
                                arabic = cleanOfflineGlossaryText(row.glossary),
                                note = row.note
                            )
                        ),
                        jlpt = 0
                    )
                )
            putCachedDetails(cacheKey, response)
            response
        }
    }

    private suspend fun WordDetailsResponse.withResolvedSentenceWordLinks(): WordDetailsResponse {
        val examples = sentenceMap.orEmpty().values + sentenceSearch
        val linkIds = examples
            .asSequence()
            .flatMap { it.wordLinks.orEmpty().asSequence() }
            .map { it.wordId }
            .filter { it > 0 }
            .distinct()
            .toList()
        if (linkIds.isEmpty()) return this

        val termsById = yomitanDao.getByIds(linkIds).associateBy { it.id }
        fun enrich(example: SentenceExample): SentenceExample {
            val enrichedLinks = example.wordLinks?.map { link ->
                val term = termsById[link.wordId] ?: return@map link
                link.copy(
                    text = link.text.ifBlank { term.expression },
                    kana = link.kana.ifBlank { term.reading }
                )
            }
            return example.copy(wordLinks = enrichedLinks)
        }

        return copy(
            sentenceMap = sentenceMap?.mapValues { (_, example) -> enrich(example) },
            sentenceSearch = sentenceSearch.map(::enrich)
        )
    }

    override suspend fun loadCategories(): Result<LoadCategoriesResponse> {
        return runIoCatching {
            synchronized(categoriesCacheLock) {
                cachedCategories?.let { return@runIoCatching it }
            }
            val categoriesJson = yomitanDao.getMetaValue("categories_json").orEmpty()
            val response = if (categoriesJson.isBlank()) {
                LoadCategoriesResponse()
            } else {
                LoadCategoriesResponse(categories = parseCategoryRefs(categoriesJson))
            }
            synchronized(categoriesCacheLock) {
                cachedCategories = response
            }
            response
        }
    }

    override suspend fun loadCategory(id: Int, page: Int): Result<LoadCategoryResponse> {
        return runIoCatching {
            val categories = loadCategories().getOrThrow().categories
            val category = categories.firstOrNull { it.id == id } ?: CategoryRef(id = id, name = "تصنيف")
            val pageIndex = page.coerceAtLeast(0)
            val pageSize = 80
            val offset = pageIndex * pageSize
            val totalCount = yomitanDao.countCategoryTerms(categoryId = id)
            val pageCount = if (totalCount == 0) 0 else ((totalCount + pageSize - 1) / pageSize)
            val pageRows = yomitanDao.loadCategoryTermsPaged(
                categoryId = id,
                limit = pageSize,
                offset = offset
            )

            LoadCategoryResponse(
                category = category,
                members = SearchWordsResponse(
                    items = pageRows.map(::toSearchItem),
                    page = pageIndex,
                    pageCount = pageCount,
                    totalCount = totalCount
                )
            )
        }
    }

    private fun getCachedDetails(key: DetailCacheKey): WordDetailsResponse? {
        return synchronized(detailCache) { detailCache[key] }
    }

    private fun putCachedDetails(key: DetailCacheKey, details: WordDetailsResponse) {
        synchronized(detailCache) { detailCache[key] = details }
    }

    private fun getJapaneseSearchPlan(query: String): JapaneseSearchPlan {
        synchronized(searchPlanCache) {
            searchPlanCache[query]?.let { return it }
        }

        val normalizedQueries = buildNormalizedJapaneseQueries(query)
        val searchSegments = normalizedQueries.flatMap(::extractSearchSegments).distinct()
        val variantsBySegment = searchSegments.associateWith(::expandJapaneseTokenVariants)
        val searchCandidates = variantsBySegment.values
            .asSequence()
            .flatten()
            .distinct()
            .toList()
        val ftsQueries = variantsBySegment.values
            .mapNotNull(::buildVariantsFtsQuery)
            .distinct()
        val plan = JapaneseSearchPlan(
            variantsBySegment = variantsBySegment,
            searchCandidates = searchCandidates,
            ftsQueries = ftsQueries
        )
        synchronized(searchPlanCache) { searchPlanCache[query] = plan }
        return plan
    }

    private fun toSearchItem(row: YomitanTermListRow): SearchItem {
        return SearchItem(
            id = row.id,
            kana = row.reading,
            writings = listOf(Writing(text = row.expression)),
            meaningSummary = buildOfflineSearchPreview(row.glossary),
            jlpt = 0,
            difficulty = row.difficulty
        )
    }

    private fun isArabicQuery(text: String): Boolean {
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }

    private fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(ARABIC_DIACRITICS_REGEX, "")
            .replace("ـ", "")
            .replace(ARABIC_ALEF_VARIANTS_REGEX, "ا")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace("ة", "ه")
            .replace(NON_LETTER_NUMBER_SPACE_REGEX, " ")
            .replace(MULTISPACE_REGEX, " ")
            .trim()
    }

    private fun scoreArabicMatch(
        query: String,
        glossary: String,
        tokens: List<String>
    ): Int {
        if (query.isBlank() || glossary.isBlank()) return Int.MAX_VALUE
        val hasAllTokens = tokens.all { glossary.contains(it) }
        if (!hasAllTokens) return Int.MAX_VALUE
        if (glossary == query) return 0
        if (glossary.startsWith("$query ")) return 1
        if (glossary.startsWith(query)) return 2
        if (containsRegexWhitespaceBoundedMatch(glossary, query)) return 3

        val index = glossary.indexOf(query)
        return if (index >= 0) 100 + index else Int.MAX_VALUE
    }

    private fun rankJapaneseQuery(candidates: List<String>, row: YomitanTermListRow): Int {
        if (candidates.isEmpty()) return Int.MAX_VALUE
        candidates.forEachIndexed { index, candidate ->
            val baseRank = index * 10
            when {
                row.expression == candidate -> return baseRank
                row.reading == candidate -> return baseRank + 1
                row.expression.startsWith(candidate) -> return baseRank + 2
                row.reading.startsWith(candidate) -> return baseRank + 3
            }
        }
        return candidates.size * 10 + 4
    }

    private fun buildNormalizedJapaneseQueries(rawQuery: String): List<String> {
        return buildList {
            add(rawQuery)
            RomajiConverter.toHiraganaIfRomaji(rawQuery)
                ?.takeIf { it != rawQuery }
                ?.let(::add)
        }.distinct()
    }

    private fun buildVariantsFtsQuery(variants: List<String>): String? {
        val sanitizedVariants = variants
            .mapNotNull(::sanitizeFtsToken)
            .distinct()

        return when {
            sanitizedVariants.isEmpty() -> null
            sanitizedVariants.size == 1 -> "${sanitizedVariants.first()}*"
            else -> sanitizedVariants.joinToString(
                separator = " OR ",
                prefix = "(",
                postfix = ")"
            ) { "$it*" }
        }
    }

    private fun extractSearchSegments(rawQuery: String): List<String> {
        return rawQuery.trim()
            .split(SENTENCE_SPLIT_REGEX)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { token -> splitJapaneseSentenceToken(token).asSequence() }
            .distinct()
            .toList()
    }

    private fun splitJapaneseSentenceToken(token: String): List<String> {
        if (token.isBlank()) return emptyList()
        if (!token.any(::isJapaneseCharacter)) return listOf(token)

        val parts = mutableListOf<String>()
        var index = 0
        var segmentStart = 0

        while (index < token.length) {
            val particle = JAPANESE_PARTICLES.firstOrNull { candidate ->
                token.startsWith(candidate, index) &&
                    index > segmentStart &&
                    index + candidate.length < token.length &&
                    isJapaneseCharacter(token[index - 1]) &&
                    isJapaneseCharacter(token[index + candidate.length])
            }

            if (particle != null) {
                token.substring(segmentStart, index)
                    .takeIf { it.isNotBlank() }
                    ?.let(parts::add)
                index += particle.length
                segmentStart = index
            } else {
                index += 1
            }
        }

        token.substring(segmentStart)
            .takeIf { it.isNotBlank() }
            ?.let(parts::add)

        return if (parts.isEmpty()) listOf(token) else parts
    }

    private fun expandJapaneseTokenVariants(token: String): List<String> {
        if (token.isBlank()) return emptyList()

        val variants = linkedSetOf<String>()
        variants.addAll(JapaneseDeinflector.generateCandidates(token))

        val trailingKanaLength = token.reversed().takeWhile(::isKana).length
        if (trailingKanaLength in 1 until token.length) {
            val stem = token.dropLast(trailingKanaLength)
            val trailingKana = token.takeLast(trailingKanaLength)
            val trailingVariants = JapaneseDeinflector.generateCandidates(trailingKana)
            if (shouldSearchBareStem(token, stem, trailingKanaLength, trailingVariants)) {
                variants.add(stem)
            }
            variants.addAll(trailingVariants)
            trailingVariants.forEach { variants.add(stem + it) }
        }

        return variants.toList()
    }

    private fun scoreJapaneseSegmentMatches(
        variantsBySegment: Collection<List<String>>,
        row: YomitanTermListRow
    ): Int {
        if (variantsBySegment.isEmpty()) return 0
        return variantsBySegment.sumOf { variants ->
            variants.maxOfOrNull { variant ->
                when {
                    row.expression == variant -> 120
                    row.reading == variant -> 110
                    row.expression.startsWith(variant) -> 80
                    row.reading.startsWith(variant) -> 70
                    row.expression.contains(variant) -> 40
                    row.reading.contains(variant) -> 30
                    else -> 0
                }
            } ?: 0
        }
    }

    private fun buildGlossaryOnlyFtsQuery(rawQuery: String): String? {
        val tokens = rawQuery.trim()
            .split(FTS_TOKEN_SPLIT_REGEX)
            .mapNotNull { token -> sanitizeFtsToken(token)?.let { "glossary:$it*" } }
            .distinct()

        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " AND ")
    }

    private fun sanitizeFtsToken(raw: String): String? {
        val trimmed = raw.trim().trim('"', '\'', '`')
        if (trimmed.isBlank()) return null

        val cleaned = trimmed
            .replace(FTS_TOKEN_RESERVED_CHARS_REGEX, "")
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun containsRegexWhitespaceBoundedMatch(text: String, query: String): Boolean {
        if (query.isEmpty()) return false
        var index = text.indexOf(query)
        while (index >= 0) {
            val end = index + query.length
            val startsAtBoundary = index == 0 || isDefaultRegexWhitespace(text[index - 1])
            val endsAtBoundary = end == text.length || isDefaultRegexWhitespace(text[end])
            if (startsAtBoundary && endsAtBoundary) return true
            index = text.indexOf(query, startIndex = index + 1)
        }
        return false
    }

    private fun isDefaultRegexWhitespace(ch: Char): Boolean {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\u000B' || ch == '\u000C' || ch == '\r'
    }

    private fun isJapaneseCharacter(ch: Char): Boolean {
        return ch in '\u3040'..'\u30FF' || ch in '\u3400'..'\u9FFF' || ch == '々'
    }

    private fun isKana(ch: Char): Boolean {
        return ch in '\u3040'..'\u30FF'
    }

    private fun shouldSearchBareStem(
        token: String,
        stem: String,
        trailingKanaLength: Int,
        trailingVariants: List<String>
    ): Boolean {
        if (stem.isBlank()) return false
        if (trailingKanaLength < 2) return false
        if (!stem.any { !isKana(it) }) return false
        val trailingKana = token.takeLast(trailingKanaLength)
        return trailingVariants.any { it != trailingKana }
    }

    private fun parseCategoryRefs(raw: String): List<CategoryRef> {
        val array = gson.fromJson(raw, JsonArray::class.java) ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, CategoryRef::class.java) }.getOrNull()
        }
    }
}
