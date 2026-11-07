package com.shinjikai.dictionary.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalYomitanSourceDeinflectionTest {
    @Test
    fun `deinflector expands common past tense forms`() {
        val candidates = JapaneseDeinflector.generateCandidates("走った")

        assertTrue(candidates.contains("走った"))
        assertTrue(candidates.contains("走る"))
    }

    @Test
    fun `offline search includes deinflected japanese forms in fts query and ranking`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 11, expression = "走り", reading = "はしり", glossary = "running"),
                term(id = 10, expression = "走る", reading = "はしる", glossary = "to run")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "走った", page = 0).getOrThrow()

        assertEquals(listOf(10, 11), result.items.map { it.id })
        assertTrue(dao.ftsQueries.any { it.contains("走った*") || it.contains("走る*") })
    }

    @Test
    fun `offline search supports mixed kanji reading polite forms`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 20, expression = "食", reading = "たべる", glossary = "to eat")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "食たべました", page = 0).getOrThrow()

        assertEquals(listOf(20), result.items.map { it.id })
        assertTrue(dao.ftsQueries.any { it.contains("食*") })
        assertTrue(dao.ftsQueries.any { it.contains("たべる*") })
    }

    @Test
    fun `offline search splits sentence style japanese queries into searchable terms`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 30, expression = "魚", reading = "さかな", glossary = "fish"),
                term(id = 31, expression = "食べる", reading = "たべる", glossary = "to eat")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "魚が食べたい", page = 0).getOrThrow()

        assertEquals(listOf(30, 31), result.items.map { it.id })
        assertTrue(dao.ftsQueries.any { it.contains("魚*") })
        assertTrue(dao.ftsQueries.any { it.contains("食べる*") })
        assertTrue(dao.ftsQueries.size >= 2)
    }

    @Test
    fun `offline search does not widen simple dictionary forms into noisy bare kanji matches`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 40, expression = "学校", reading = "がっこう", glossary = "school"),
                term(id = 41, expression = "行く", reading = "いく", glossary = "to go")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "学校へ行く", page = 0).getOrThrow()

        assertEquals(listOf(40, 41), result.items.map { it.id })
        assertTrue(dao.ftsQueries.any { it.contains("学校*") })
        assertTrue(dao.ftsQueries.any { it.contains("行く*") })
        assertTrue(dao.ftsQueries.none { it.contains("行*") && !it.contains("行く*") })
    }

    @Test
    fun `offline search splits romaji sentence queries after transliteration`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 50, expression = "魚", reading = "さかな", glossary = "fish"),
                term(id = 51, expression = "食べる", reading = "たべる", glossary = "to eat")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "sakanagatabetai", page = 0).getOrThrow()

        assertEquals(setOf(50, 51), result.items.map { it.id }.toSet())
        assertTrue(dao.ftsQueries.isNotEmpty())
    }

    @Test
    fun `deinflector follows yomitan japanese transform chains`() {
        val cases = mapOf(
            "食べさせられなかった" to "食べる",
            "愛しくありませんでした" to "愛しい",
            "読んでいる" to "読む",
            "いらっしゃいます" to "いらっしゃる",
            "行かなかった" to "行く"
        )

        for ((inflected, dictionaryForm) in cases) {
            val candidates = JapaneseDeinflector.generateCandidates(inflected)

            assertTrue("$inflected should deinflect to $dictionaryForm", candidates.contains(dictionaryForm))
        }
    }

    private class FakeYomitanDao : YomitanDao {
        var ftsResults: List<YomitanTermListRow> = emptyList()
        val ftsQueries = mutableListOf<String>()

        override suspend fun search(term: String, prefix: String, limit: Int): List<YomitanTermListRow> = emptyList()

        override suspend fun searchPaged(term: String, prefix: String, limit: Int, offset: Int): List<YomitanTermListRow> = emptyList()

        override suspend fun searchFts(matchQuery: String, limit: Int): List<YomitanTermListRow> {
            ftsQueries += matchQuery
            return ftsResults.take(limit)
        }

        override suspend fun searchGlossaryFts(matchQuery: String, limit: Int): List<YomitanArabicSearchRow> = emptyList()

        override suspend fun searchArabic(term: String, normalizedTerm: String, limit: Int): List<YomitanArabicSearchRow> = emptyList()

        override suspend fun countSearchMatches(term: String): Int = 0

        override suspend fun getById(id: Int): YomitanTermEntity? = null

        override suspend fun getByIds(ids: List<Int>): List<YomitanTermListRow> = emptyList()

        override suspend fun loadAllTerms(): List<YomitanTermEntity> = emptyList()

        override suspend fun browseTermsAfter(
            lastReading: String?,
            lastExpression: String?,
            lastId: Int?,
            limit: Int
        ): List<YomitanTermListRow> = emptyList()

        override suspend fun upsertCategoryRefs(items: List<YomitanTermCategoryEntity>) = Unit

        override suspend fun clearCategoryRefs() = Unit

        override suspend fun deleteCategoryRefsForSource(source: String) = Unit

        override suspend fun deleteTermsFtsForSource(source: String) = Unit

        override suspend fun deleteTermsForSource(source: String) = Unit

        override suspend fun nextNegativeTermId(): Int = -1

        override suspend fun setSourceEnabled(source: String, enabled: Boolean) = Unit

        override suspend fun countCategoryRefs(): Int = 0

        override suspend fun loadCategoryTermsPaged(categoryId: Int, limit: Int, offset: Int): List<YomitanTermListRow> = emptyList()

        override suspend fun countCategoryTerms(categoryId: Int): Int = 0

        override suspend fun upsertAll(items: List<YomitanTermEntity>) = Unit

        override suspend fun upsertAllFts(items: List<YomitanTermFtsEntity>) = Unit

        override suspend fun clearTerms() = Unit

        override suspend fun clearTermsFts() = Unit

        override suspend fun countTerms(): Int = 0

        override suspend fun countTermsBySource(source: String): Int = 0

        override suspend fun countFtsTermsBySource(source: String): Int = 0

        override suspend fun loadPreviewTerms(limit: Int): List<YomitanTermListRow> = emptyList()

        override suspend fun countFeaturedTerms(minDifficulty: Int): Int = 0

        override suspend fun loadFeaturedTermAt(minDifficulty: Int, offset: Int): YomitanTermListRow? = null

        override suspend fun loadRandomFeaturedTerms(
            minDifficulty: Int,
            excludeId: Int,
            limit: Int
        ): List<YomitanTermListRow> = emptyList()

        override suspend fun loadRandomTerm(): YomitanTermListRow? = null

        override suspend fun upsertMeta(meta: YomitanMetaEntity) = Unit

        override suspend fun getMetaValue(key: String): String? = null

        override suspend fun deleteMeta(key: String) = Unit
    }

    private companion object {
        fun term(
            id: Int,
            expression: String,
            reading: String = expression,
            glossary: String
        ) = YomitanTermListRow(
            id = id,
            expression = expression,
            reading = reading,
            glossary = glossary
        )
    }
}
