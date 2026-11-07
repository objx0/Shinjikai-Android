package com.shinjikai.dictionary.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "yomitan_terms",
    indices = [
        Index(value = ["expression"]),
        Index(value = ["reading"]),
        Index(value = ["reading", "expression", "id"], name = "index_yomitan_terms_browse")
    ]
)
data class YomitanTermEntity(
    @PrimaryKey val id: Int,
    val expression: String,
    val reading: String,
    val glossary: String,
    val difficulty: Int = 0,
    val note: String = "",
    val source: String = "yomitan",
    val enabled: Boolean = true,
    val detailsJson: String? = null,
    val jlpt: Int = 0,
    /** Normalized Arabic phrases used for ranking; see [ArabicSearch.buildSearchText]. */
    val searchText: String = ""
)

data class YomitanTermListRow(
    val id: Int,
    val expression: String,
    val reading: String,
    val glossary: String,
    val difficulty: Int = 0
)

/** Row used by Arabic search: carries the fields needed to rank results the way shinjikai.app does. */
data class YomitanArabicSearchRow(
    val id: Int,
    val expression: String,
    val reading: String,
    val glossary: String,
    val difficulty: Int = 0,
    val jlpt: Int = 0,
    val searchText: String = ""
) {
    fun toListRow(): YomitanTermListRow = YomitanTermListRow(
        id = id,
        expression = expression,
        reading = reading,
        glossary = glossary,
        difficulty = difficulty
    )
}

/**
 * The FTS `glossary` column holds the normalized Arabic search text when available
 * (falls back to the raw glossary), so diacritics and letter variants never block a match.
 */
@Fts4
@Entity(tableName = "yomitan_terms_fts")
data class YomitanTermFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val expression: String,
    val reading: String,
    val glossary: String
)

@Entity(tableName = "yomitan_meta")
data class YomitanMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "yomitan_term_categories",
    primaryKeys = ["termId", "categoryId"],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["termId"])
    ]
)
data class YomitanTermCategoryEntity(
    val termId: Int,
    val categoryId: Int
)
