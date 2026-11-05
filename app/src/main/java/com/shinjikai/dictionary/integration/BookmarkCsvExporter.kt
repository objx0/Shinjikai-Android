package com.shinjikai.dictionary.integration

import com.shinjikai.dictionary.data.BookmarkItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookmarkCsvExporter {
    fun suggestedFileName(epochMs: Long = System.currentTimeMillis()): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS", Locale.ROOT)
            .format(Date(epochMs))
        return "shinjikai-bookmarks-$timestamp.csv"
    }

    fun toCsv(bookmarks: List<BookmarkItem>): String = buildString {
        // A UTF-8 BOM helps spreadsheet apps recognize Japanese and Arabic text.
        append('\uFEFF')
        append("Word,Reading,Meaning\r\n")
        bookmarks.forEach { bookmark ->
            appendRow(bookmark.item.primaryWriting)
            append(',')
            appendRow(bookmark.item.kana)
            append(',')
            appendRow(bookmark.item.meaningSummary)
            append("\r\n")
        }
    }

    private fun StringBuilder.appendRow(value: String) {
        append('"')
        append(value.replace("\"", "\"\""))
        append('"')
    }
}
