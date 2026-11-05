package com.shinjikai.dictionary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface YomitanDao {
    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND (
                expression LIKE '%' || :term || '%'
                OR reading LIKE '%' || :term || '%'
                OR glossary LIKE '%' || :term || '%'
            )
        ORDER BY
            CASE
                WHEN expression = :term THEN 0
                WHEN reading = :term THEN 1
                WHEN expression LIKE :prefix THEN 2
                WHEN reading LIKE :prefix THEN 3
                ELSE 4
            END,
            id ASC
        LIMIT :limit
        """
    )
    suspend fun search(term: String, prefix: String, limit: Int = 80): List<YomitanTermListRow>

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND (
                expression LIKE '%' || :term || '%'
                OR reading LIKE '%' || :term || '%'
                OR glossary LIKE '%' || :term || '%'
            )
        ORDER BY
            CASE
                WHEN expression = :term THEN 0
                WHEN reading = :term THEN 1
                WHEN expression LIKE :prefix THEN 2
                WHEN reading LIKE :prefix THEN 3
                ELSE 4
            END,
            id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchPaged(term: String, prefix: String, limit: Int, offset: Int): List<YomitanTermListRow>

    @Query(
        """
        SELECT t.id, t.expression, t.reading, t.glossary, t.difficulty
        FROM yomitan_terms t
        JOIN yomitan_terms_fts ON t.id = yomitan_terms_fts.rowid
        WHERE yomitan_terms_fts MATCH :matchQuery
            AND t.enabled = 1
            AND (t.expression <> '' OR t.reading <> '')
        LIMIT :limit
        """
    )
    suspend fun searchFts(matchQuery: String, limit: Int = 250): List<YomitanTermListRow>

    @Query(
        """
        SELECT t.id, t.expression, t.reading, t.glossary, t.difficulty
        FROM yomitan_terms t
        JOIN yomitan_terms_fts ON t.id = yomitan_terms_fts.rowid
        WHERE yomitan_terms_fts MATCH :matchQuery
            AND t.enabled = 1
            AND (t.expression <> '' OR t.reading <> '')
        LIMIT :limit
        """
    )
    suspend fun searchGlossaryFts(matchQuery: String, limit: Int = 2500): List<YomitanTermListRow>

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND (
                glossary LIKE '%' || :term || '%'
                OR glossary LIKE '%' || :normalizedTerm || '%'
            )
        ORDER BY id ASC
        LIMIT :limit
        """
    )
    suspend fun searchArabic(
        term: String,
        normalizedTerm: String,
        limit: Int = 2500
    ): List<YomitanTermListRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND (
                expression LIKE '%' || :term || '%'
                OR reading LIKE '%' || :term || '%'
                OR glossary LIKE '%' || :term || '%'
            )
        """
    )
    suspend fun countSearchMatches(term: String): Int

    @Query("SELECT * FROM yomitan_terms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): YomitanTermEntity?

    @Query("SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms WHERE enabled = 1 AND id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<YomitanTermListRow>

    @Query("SELECT * FROM yomitan_terms WHERE TRIM(expression) <> '' OR TRIM(reading) <> ''")
    suspend fun loadAllTerms(): List<YomitanTermEntity>

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND (
                :lastReading IS NULL
                OR reading > :lastReading
                OR (reading = :lastReading AND expression > :lastExpression)
                OR (reading = :lastReading AND expression = :lastExpression AND id > :lastId)
            )
        ORDER BY reading ASC, expression ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun browseTermsAfter(
        lastReading: String?,
        lastExpression: String?,
        lastId: Int?,
        limit: Int
    ): List<YomitanTermListRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryRefs(items: List<YomitanTermCategoryEntity>)

    @Query("DELETE FROM yomitan_term_categories")
    suspend fun clearCategoryRefs()

    @Query("DELETE FROM yomitan_term_categories WHERE termId IN (SELECT id FROM yomitan_terms WHERE source = :source)")
    suspend fun deleteCategoryRefsForSource(source: String)

    @Query("DELETE FROM yomitan_terms_fts WHERE rowid IN (SELECT id FROM yomitan_terms WHERE source = :source)")
    suspend fun deleteTermsFtsForSource(source: String)

    @Query("DELETE FROM yomitan_terms WHERE source = :source")
    suspend fun deleteTermsForSource(source: String)

    @Query("SELECT COALESCE(MIN(id), 0) - 1 FROM yomitan_terms WHERE id < 0")
    suspend fun nextNegativeTermId(): Int

    @Query("UPDATE yomitan_terms SET enabled = :enabled WHERE source = :source")
    suspend fun setSourceEnabled(source: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM yomitan_term_categories")
    suspend fun countCategoryRefs(): Int

    @Query(
        """
        SELECT t.id, t.expression, t.reading, t.glossary, t.difficulty
        FROM yomitan_terms t
        INNER JOIN yomitan_term_categories c ON c.termId = t.id
        WHERE c.categoryId = :categoryId
            AND t.enabled = 1
            AND (t.expression <> '' OR t.reading <> '')
        ORDER BY t.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun loadCategoryTermsPaged(
        categoryId: Int,
        limit: Int,
        offset: Int
    ): List<YomitanTermListRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM yomitan_term_categories c
        INNER JOIN yomitan_terms t ON t.id = c.termId
        WHERE c.categoryId = :categoryId
            AND t.enabled = 1
            AND (t.expression <> '' OR t.reading <> '')
        """
    )
    suspend fun countCategoryTerms(categoryId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<YomitanTermEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllFts(items: List<YomitanTermFtsEntity>)

    @Query("DELETE FROM yomitan_terms")
    suspend fun clearTerms()

    @Query("DELETE FROM yomitan_terms_fts")
    suspend fun clearTermsFts()

    @Query("SELECT COUNT(*) FROM yomitan_terms WHERE expression <> '' OR reading <> ''")
    suspend fun countTerms(): Int

    @Query("SELECT COUNT(*) FROM yomitan_terms WHERE source = :source")
    suspend fun countTermsBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM yomitan_terms_fts WHERE rowid IN (SELECT id FROM yomitan_terms WHERE source = :source)")
    suspend fun countFtsTermsBySource(source: String): Int

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1 AND (expression <> '' OR reading <> '')
        ORDER BY id ASC
        LIMIT :limit
        """
    )
    suspend fun loadPreviewTerms(limit: Int = 6): List<YomitanTermListRow>

    /** Terms eligible for the search landing (word of the day / suggestions). */
    @Query(
        """
        SELECT COUNT(*) FROM yomitan_terms
        WHERE enabled = 1 AND expression <> '' AND difficulty >= :minDifficulty
        """
    )
    suspend fun countFeaturedTerms(minDifficulty: Int): Int

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1 AND expression <> '' AND difficulty >= :minDifficulty
        ORDER BY id ASC
        LIMIT 1 OFFSET :offset
        """
    )
    suspend fun loadFeaturedTermAt(minDifficulty: Int, offset: Int): YomitanTermListRow?

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1 AND expression <> '' AND difficulty >= :minDifficulty AND id <> :excludeId
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun loadRandomFeaturedTerms(minDifficulty: Int, excludeId: Int, limit: Int): List<YomitanTermListRow>

    @Query(
        """
        SELECT id, expression, reading, glossary, difficulty FROM yomitan_terms
        WHERE enabled = 1
            AND (expression <> '' OR reading <> '')
            AND id >= (
                (RANDOM() & 2147483647) %
                (SELECT COALESCE(MAX(id), 0) + 1 FROM yomitan_terms)
            )
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun loadRandomTerm(): YomitanTermListRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: YomitanMetaEntity)

    @Query("SELECT value FROM yomitan_meta WHERE key = :key LIMIT 1")
    suspend fun getMetaValue(key: String): String?

    @Query("DELETE FROM yomitan_meta WHERE key = :key")
    suspend fun deleteMeta(key: String)
}
