package com.shinjikai.dictionary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.shinjikai.dictionary.data.WritingPart

@Composable
internal fun HeadwordFuriganaText(
    text: String,
    reading: String,
    parts: List<WritingPart>?,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    rubyStyle: TextStyle = MaterialTheme.typography.labelMedium,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    rubyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign = TextAlign.End,
    autoFit: Boolean = false
) {
    val cleanText = text.trim()
    if (cleanText.isBlank()) return

    if (autoFit) {
        // Long headwords (e.g. 老齢基礎年金) shrink to stay on one line instead of
        // wrapping and clipping, so the furigana stays centered over the whole word.
        BoxWithConstraints(modifier = modifier) {
            val textMeasurer = rememberTextMeasurer()
            val maxWidthPx = constraints.maxWidth
            val fittedStyle = remember(cleanText, baseStyle, maxWidthPx) {
                fitHeadwordStyle(textMeasurer, cleanText, baseStyle, maxWidthPx)
            }
            HeadwordFuriganaText(
                text = cleanText,
                reading = reading,
                parts = parts,
                modifier = Modifier.fillMaxWidth(),
                baseStyle = fittedStyle,
                rubyStyle = rubyStyle,
                baseColor = baseColor,
                rubyColor = rubyColor,
                textAlign = textAlign,
                autoFit = false
            )
        }
        return
    }

    val cleanReading = reading.trim()
    val writingParts = parts.orEmpty()
    val segments = remember(cleanText, cleanReading, writingParts) {
        HeadwordFuriganaSegmentCache.getOrPut(
            text = cleanText,
            reading = cleanReading,
            parts = writingParts
        )
    }
    if (segments.none { !it.ruby.isNullOrBlank() }) {
        Text(
            text = cleanText,
            modifier = modifier,
            style = baseStyle.withoutFontPadding().copy(textDirection = TextDirection.ContentOrLtr),
            color = baseColor,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val layoutSegments = remember(segments) {
        segments.map { segment ->
            RubyTextSegment(base = segment.base, ruby = segment.ruby)
        }
    }
    RubyTextLayout(
        segments = layoutSegments,
        modifier = modifier.fillMaxWidth(),
        baseStyle = baseStyle.copy(textDirection = TextDirection.ContentOrLtr),
        rubyStyle = rubyStyle.copy(
            textDirection = TextDirection.ContentOrLtr,
            // Prevent the theme's generous label line height from making the
            // kana box taller than the visible kana glyphs.
            lineHeight = rubyStyle.fontSize.takeUnless { it == TextUnit.Unspecified }
                ?: rubyStyle.lineHeight
        ),
        baseColor = { baseColor },
        rubyColor = { rubyColor },
        textAlign = textAlign
    )
}

private const val MinHeadwordScale = 0.5f

private fun fitHeadwordStyle(
    textMeasurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    maxWidthPx: Int
): TextStyle {
    if (maxWidthPx == Constraints.Infinity || maxWidthPx <= 0 || !baseStyle.fontSize.isSpecified) return baseStyle
    val width = textMeasurer.measure(
        text = text,
        style = baseStyle.withoutFontPadding(),
        maxLines = 1,
        softWrap = false
    ).size.width
    if (width <= maxWidthPx || width == 0) return baseStyle
    val scale = (maxWidthPx.toFloat() / width * 0.97f).coerceAtLeast(MinHeadwordScale)
    return baseStyle.copy(
        fontSize = baseStyle.fontSize * scale,
        lineHeight = if (baseStyle.lineHeight.isSpecified) baseStyle.lineHeight * scale else baseStyle.lineHeight
    )
}

internal data class HeadwordRubySegment(
    val base: String,
    val ruby: String?
)

/**
 * Keeps parsed headwords available after a lazy-list item or navigation destination leaves
 * composition. Compose's [remember] only covers the lifetime of one composed card, while this
 * bounded process cache avoids repeating the alignment work when users return to a tab.
 */
private object HeadwordFuriganaSegmentCache {
    private const val MaxEntries = 512

    private data class Key(
        val text: String,
        val reading: String,
        val parts: List<WritingPart>
    )

    private val entries = object : LinkedHashMap<Key, List<HeadwordRubySegment>>(
        MaxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Key, List<HeadwordRubySegment>>
        ): Boolean = size > MaxEntries
    }

    @Synchronized
    fun getOrPut(
        text: String,
        reading: String,
        parts: List<WritingPart>
    ): List<HeadwordRubySegment> {
        val key = Key(text = text, reading = reading, parts = parts.toList())
        return entries[key] ?: buildHeadwordFuriganaSegments(text, reading, parts)
            .also { entries[key] = it }
    }
}

private data class HeadwordRubyToken(
    val base: String,
    val kanji: Boolean,
    val readingHint: String? = null
)

internal fun buildHeadwordFuriganaSegments(
    text: String,
    reading: String,
    parts: List<WritingPart>
): List<HeadwordRubySegment> {
    buildSegmentsFromWritingParts(text, parts)?.let { return it }
    val tokens = tokenizeHeadword(text)
    val readings = alignHeadwordReadings(tokens, reading).orEmpty()
    return tokens.mapIndexed { index, token ->
        HeadwordRubySegment(
            base = token.base,
            ruby = readings.getOrNull(index)
                ?.takeIf { token.kanji && it.isNotBlank() && it != token.base }
        )
    }
}

private fun buildSegmentsFromWritingParts(
    text: String,
    parts: List<WritingPart>
): List<HeadwordRubySegment>? {
    if (parts.isEmpty()) return null
    val segments = mutableListOf<HeadwordRubySegment>()
    var pendingKanjiBase = StringBuilder()
    var pendingKanjiRuby = StringBuilder()
    var partIndex = 0
    var textIndex = 0
    var matched = false

    fun flushPendingKanji() {
        if (pendingKanjiBase.isEmpty()) return
        val base = pendingKanjiBase.toString()
        val ruby = pendingKanjiRuby.toString().takeIf { it.isNotBlank() && it != base }
        segments += HeadwordRubySegment(base = base, ruby = ruby)
        pendingKanjiBase = StringBuilder()
        pendingKanjiRuby = StringBuilder()
    }

    while (textIndex < text.length) {
        val codePoint = Character.codePointAt(text, textIndex)
        val charText = String(Character.toChars(codePoint))
        val isKanji = isJapaneseRubyKanjiLikeCodePoint(codePoint)
        val part = parts.getOrNull(partIndex)
        val ruby = if (
            part != null &&
            part.kanji == codePoint &&
            part.reading.isNotBlank()
        ) {
            matched = true
            partIndex += 1
            part.reading.trim()
        } else {
            null
        }

        if (isKanji && ruby != null && ruby != charText) {
            // Each kanji keeps its own reading so the furigana sits right above it
            // (老齢基礎年金 -> ろう|れい|き|そ|ねん|きん), instead of one block over the word.
            flushPendingKanji()
            segments += HeadwordRubySegment(base = charText, ruby = ruby)
        } else {
            flushPendingKanji()
            segments += HeadwordRubySegment(
                base = charText,
                ruby = ruby?.takeIf { isKanji && it != charText }
            )
        }
        textIndex += Character.charCount(codePoint)
    }
    flushPendingKanji()
    return segments.takeIf { matched }
}

private fun tokenizeHeadword(text: String): List<HeadwordRubyToken> {
    val tokens = mutableListOf<HeadwordRubyToken>()
    var index = 0
    while (index < text.length) {
        val start = index
        val kanji = isHeadwordKanji(text[index])
        if (kanji) {
            while (index < text.length && isHeadwordKanji(text[index])) index += 1
        } else {
            index += 1
            while (index < text.length && !isHeadwordKanji(text[index])) index += 1
        }
        tokens += HeadwordRubyToken(
            base = text.substring(start, index),
            kanji = kanji
        )
    }
    return tokens
}

private fun alignHeadwordReadings(
    tokens: List<HeadwordRubyToken>,
    reading: String
): List<String?>? {
    if (reading.isBlank()) return tokens.map { null as String? }
    val memo = mutableMapOf<Pair<Int, Int>, List<String?>?>()

    fun solve(tokenIndex: Int, readingIndex: Int): List<String?>? {
        val key = tokenIndex to readingIndex
        if (key in memo) return memo[key]
        if (tokenIndex >= tokens.size) {
            val result = if (readingIndex == reading.length) emptyList<String?>() else null
            memo[key] = result
            return result
        }

        val token = tokens[tokenIndex]
        val result = if (token.kanji) {
            var matched: List<String?>? = null
            for (end in reading.length downTo (readingIndex + 1)) {
                val tail = solve(tokenIndex + 1, end) ?: continue
                matched = listOf(reading.substring(readingIndex, end)) + tail
                break
            }
            matched
        } else {
            val consumed = consumeHeadwordLiteral(token.base, reading, readingIndex)
            if (consumed >= 0) {
                solve(tokenIndex + 1, readingIndex + consumed)?.let { listOf(null) + it }
            } else {
                null
            }
        }
        memo[key] = result
        return result
    }

    return solve(0, 0) ?: alignHeadwordReadingsGreedy(tokens, reading)
}

private fun alignHeadwordReadingsGreedy(
    tokens: List<HeadwordRubyToken>,
    reading: String
): List<String?> {
    val readings = MutableList<String?>(tokens.size) { null }
    var readingIndex = 0
    tokens.forEachIndexed { index, token ->
        if (token.kanji) {
            val nextLiteral = tokens.drop(index + 1)
                .firstOrNull { !it.kanji }
                ?.base
                ?.firstOrNull()
            val end = nextLiteral
                ?.let { literal ->
                    (reading.length - 1 downTo readingIndex)
                        .firstOrNull { headwordLiteralEquals(literal, reading[it]) }
                }
                ?: reading.length
            val safeEnd = end.coerceIn(readingIndex, reading.length)
            readings[index] = reading.substring(readingIndex, safeEnd)
            readingIndex = safeEnd
        } else {
            val consumed = consumeHeadwordLiteral(token.base, reading, readingIndex)
            if (consumed >= 0) readingIndex += consumed
        }
    }
    return readings
}

private fun consumeHeadwordLiteral(base: String, reading: String, start: Int): Int {
    var readingIndex = start
    base.forEach { char ->
        when {
            char.isWhitespace() -> Unit
            readingIndex < reading.length && headwordLiteralEquals(char, reading[readingIndex]) -> readingIndex += 1
            else -> return -1
        }
    }
    return readingIndex - start
}

private fun isHeadwordKanji(char: Char): Boolean {
    return isJapaneseRubyKanjiLike(char)
}

private fun isHeadwordKanjiCodePoint(codePoint: Int): Boolean {
    val block = Character.UnicodeBlock.of(codePoint)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        codePoint == 0x3005 ||
        codePoint == 0x3006 ||
        codePoint == 0x30F6 ||
        codePoint == '々'.code ||
        codePoint == '〆'.code
}

private fun headwordLiteralEquals(textChar: Char, readingChar: Char): Boolean {
    return japaneseRubyLiteralEquals(textChar, readingChar)
}

private fun normalizeHeadwordLiteral(char: Char): Char {
    return when (char) {
        'ー', 'ｰ' -> 'ー'
        '。', '.' -> '。'
        '、', ',' -> '、'
        else -> char
    }
}
