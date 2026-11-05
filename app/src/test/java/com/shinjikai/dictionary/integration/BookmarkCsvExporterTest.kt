package com.shinjikai.dictionary.integration

import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.data.Writing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkCsvExporterTest {
    @Test
    fun `csv escapes special characters and preserves Japanese and Arabic`() {
        val bookmark = BookmarkItem(
            item = SearchItem(
                id = 1,
                kana = "きょう,あした",
                writings = listOf(Writing("今日")),
                meaningSummary = "اليوم, \"today\"\nغداً"
            ),
            createdAt = 0L
        )

        assertEquals(
            "\uFEFFWord,Reading,Meaning\r\n\"今日\",\"きょう,あした\",\"اليوم, \"\"today\"\"\nغداً\"\r\n",
            BookmarkCsvExporter.toCsv(listOf(bookmark))
        )
    }

    @Test
    fun `suggested file names include timestamp with milliseconds`() {
        val firstName = BookmarkCsvExporter.suggestedFileName(1_790_000_000_000L)
        val nextMillisecond = BookmarkCsvExporter.suggestedFileName(1_790_000_000_001L)

        assertTrue(firstName.startsWith("shinjikai-bookmarks-"))
        assertTrue(firstName.endsWith(".csv"))
        assertTrue(firstName != nextMillisecond)
    }
}
