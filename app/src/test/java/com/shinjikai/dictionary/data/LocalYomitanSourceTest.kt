package com.shinjikai.dictionary.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalYomitanSourceTest {
    @Test
    fun `arabic search normalizes query and ranks strongest matches first`() = runTest {
        val dao = FakeYomitanDao().apply {
            glossaryFtsResults = listOf(
                term(id = 30, expression = "ثالث", glossary = "هذا الايمان راسخ"),
                term(id = 10, expression = "أول", glossary = "إيمان"),
                term(id = 20, expression = "ثان", glossary = "ايمان قوي")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "إِيمان", page = 0).getOrThrow()

        assertEquals(listOf(10, 20, 30), result.items.map { it.id })
        assertEquals("glossary:إِيمان*", dao.lastGlossaryFtsQuery)
        assertEquals(1, result.pageCount)
        assertEquals(3, result.totalCount)
    }

    @Test
    fun `arabic search ranks whitespace bounded matches before embedded substrings`() = runTest {
        val query = "\u062D\u0628"
        val dao = FakeYomitanDao().apply {
            glossaryFtsResults = listOf(
                term(id = 2, expression = "embedded", glossary = "\u0633${query}\u0631"),
                term(id = 1, expression = "whole", glossary = "\u0641\u064A $query \u0643\u0628\u064A\u0631")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = query, page = 0).getOrThrow()

        assertEquals(listOf(1, 2), result.items.map { it.id })
    }

    @Test
    fun `japanese search uses ranked fts results and removes duplicates`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 4, expression = "食パン", reading = "しょくぱん", glossary = "خبز"),
                term(id = 2, expression = "食べる", reading = "たべる", glossary = "يأكل"),
                term(id = 3, expression = "食べる", reading = "たべる", glossary = "يأكل"),
                term(id = 1, expression = "食", reading = "しょく", glossary = "أكل")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "食", page = 0).getOrThrow()

        assertEquals(listOf(1, 2, 4), result.items.map { it.id })
        assertEquals("食*", dao.lastFtsQuery)
        assertEquals(3, result.totalCount)
        assertTrue(result.items.none { it.id == 3 })
    }

    @Test
    fun `offline search transliterates romaji queries before matching`() = runTest {
        val dao = FakeYomitanDao().apply {
            ftsResults = listOf(
                term(id = 7, expression = "食べる", reading = "たべる", glossary = "to eat")
            )
        }

        val result = LocalYomitanSource(dao).searchWords(term = "taberu", page = 0).getOrThrow()

        assertEquals(listOf(7), result.items.map { it.id })
        assertTrue(dao.ftsQueries.any { it.contains("たべる*") })
    }

    private class FakeYomitanDao : YomitanDao {
        var ftsResults: List<YomitanTermListRow> = emptyList()
        var glossaryFtsResults: List<YomitanTermListRow> = emptyList()
        var directSearchResults: List<YomitanTermListRow> = emptyList()
        var lastFtsQuery: String? = null
        var lastGlossaryFtsQuery: String? = null
        val ftsQueries = mutableListOf<String>()

        override suspend fun search(term: String, prefix: String, limit: Int): List<YomitanTermListRow> = directSearchResults

        override suspend fun searchPaged(term: String, prefix: String, limit: Int, offset: Int): List<YomitanTermListRow> =
            directSearchResults.drop(offset).take(limit)

        override suspend fun searchFts(matchQuery: String, limit: Int): List<YomitanTermListRow> {
            lastFtsQuery = matchQuery
            ftsQueries += matchQuery
            return ftsResults.take(limit)
        }

        override suspend fun searchGlossaryFts(matchQuery: String, limit: Int): List<YomitanTermListRow> {
            lastGlossaryFtsQuery = matchQuery
            return glossaryFtsResults.take(limit)
        }

        override suspend fun searchArabic(term: String, normalizedTerm: String, limit: Int): List<YomitanTermListRow> =
            directSearchResults.take(limit)

        override suspend fun countSearchMatches(term: String): Int = directSearchResults.size

        override suspend fun getById(id: Int): YomitanTermEntity? = null

        override suspend fun getByIds(ids: List<Int>): List<YomitanTermListRow> = emptyList()

        override suspend fun loadAllTerms(): List<YomitanTermEntity> = emptyList()

        override suspend fun browseTermsAfter(
            lastReading: String?,
            lastExpression: String?,
            lastId: Int?,
            limit: Int
        ): List<YomitanTermListRow> = directSearchResults
            .sortedWith(compareBy<YomitanTermListRow>({ it.reading }, { it.expression }, { it.id }))
            .dropWhile { row ->
                lastReading != null && (
                    row.reading < lastReading ||
                        (row.reading == lastReading && row.expression < lastExpression.orEmpty()) ||
                        (row.reading == lastReading && row.expression == lastExpression && row.id <= (lastId ?: 0))
                    )
            }
            .take(limit)

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

        override suspend fun loadRandomTerm(): YomitanTermListRow? = directSearchResults.firstOrNull()

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
