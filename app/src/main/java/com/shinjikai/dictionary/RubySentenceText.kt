package com.shinjikai.dictionary

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.shinjikai.dictionary.data.SentenceWordLink

@Composable
internal fun RubyJapaneseText(
    text: String,
    kana: String,
    links: List<SentenceWordLink>,
    onWordClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightWordId: Int? = null,
    highlightTerms: List<String> = emptyList()
) {
    val segments = remember(text, kana, links) {
        buildRubySegments(text = text, kana = kana, links = links)
    }
    if (segments.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
            textAlign = TextAlign.Left
        )
        return
    }

    val colors = MaterialTheme.colorScheme
    val highlightMode = highlightWordId != null || highlightTerms.isNotEmpty()
    val highlightRanges = remember(text, links, highlightWordId, highlightTerms) {
        if (highlightMode) findHeadwordRanges(text, links, highlightWordId, highlightTerms) else emptyList()
    }
    val baseStyle = MaterialTheme.typography.titleMedium.copy(
        textDirection = TextDirection.ContentOrLtr,
        lineHeight = 25.sp
    )
    val rubyStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 10.sp,
        textDirection = TextDirection.ContentOrLtr
    )
    val layoutSegments = remember(segments, highlightRanges) {
        segments.map { segment ->
            RubyTextSegment(
                base = segment.base,
                ruby = segment.ruby,
                wordId = segment.wordId,
                wrapGroupId = segment.wrapGroupId,
                highlighted = highlightRanges.any { range ->
                    segment.start <= range.last && segment.end > range.first
                }
            )
        }
    }
    RubyTextLayout(
        segments = layoutSegments,
        baseStyle = baseStyle,
        rubyStyle = rubyStyle,
        baseColor = { segment ->
            when {
                segment.highlighted -> colors.onSurface
                highlightMode -> colors.onSurface
                segment.wordId != null -> colors.primary
                else -> colors.onSurface
            }
        },
        rubyColor = { segment ->
            when {
                segment.highlighted -> colors.primary
                highlightMode -> colors.onSurfaceVariant
                segment.wordId != null -> colors.primary
                else -> colors.onSurfaceVariant
            }
        },
        highlightColor = if (highlightMode) colors.primary.copy(alpha = HEADWORD_MARKER_ALPHA) else Color.Unspecified,
        onWordClick = onWordClick,
        modifier = modifier
    )
}

private fun findHeadwordRanges(
    text: String,
    links: List<SentenceWordLink>,
    wordId: Int?,
    terms: List<String>
): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    if (wordId != null && wordId > 0) {
        links.filter { it.wordId == wordId && it.start >= 0 && it.end > it.start && it.end <= text.length }
            .forEach { ranges += it.start until it.end }
    }
    if (ranges.isNotEmpty()) return ranges
    for (term in terms) {
        if (term.isBlank()) continue
        var index = text.indexOf(term)
        while (index >= 0) {
            ranges += index until index + term.length
            index = text.indexOf(term, index + term.length)
        }
        if (ranges.isNotEmpty()) break
    }
    return ranges
}

internal data class RubySegment(
    val base: String,
    val ruby: String?,
    val start: Int,
    val end: Int,
    val wordId: Int?,
    val wrapGroupId: Int? = null
)

private data class RubyToken(
    val base: String,
    val start: Int,
    val end: Int,
    val kanji: Boolean,
    val wordId: Int? = null,
    val readingHint: String? = null,
    val wrapGroupId: Int? = null
)

internal fun buildRubySegments(
    text: String,
    kana: String,
    links: List<SentenceWordLink>
): List<RubySegment> {
    if (text.isBlank()) return emptyList()
    val validLinks = links.filter { link ->
        link.wordId > 0 && link.start >= 0 && link.end > link.start && link.end <= text.length
    }.sortedWith(
        compareBy<SentenceWordLink> { it.start }.thenByDescending { it.end }
    )
    val tokens = tokenizeRubyText(text, validLinks)
    if (tokens.isEmpty()) return emptyList()
    val readings = alignRubyReadings(tokens, kana).orEmpty()
    return tokens.mapIndexed { index, token ->
        RubySegment(
            base = token.base,
            ruby = readings.getOrNull(index)
                ?.takeIf { token.kanji && it.isNotBlank() && it != token.base },
            start = token.start,
            end = token.end,
            wordId = token.wordId,
            wrapGroupId = token.wrapGroupId
        )
    }
}

private fun tokenizeRubyText(
    text: String,
    links: List<SentenceWordLink> = emptyList()
): List<RubyToken> {
    val tokens = mutableListOf<RubyToken>()

    fun addFallbackTokens(
        start: Int,
        end: Int,
        wordId: Int? = null,
        wrapGroupId: Int? = null,
        readingHint: String? = null
    ) {
        var index = start
        while (index < end) {
            val tokenStart = index
            val kanji = isRubyKanji(text[index])
            if (kanji) {
                while (index < end && isRubyKanji(text[index])) index += 1
            } else {
                index += 1
                while (index < end && !isRubyKanji(text[index])) index += 1
            }
            tokens += RubyToken(
                base = text.substring(tokenStart, index),
                start = tokenStart,
                end = index,
                kanji = kanji,
                wordId = wordId,
                wrapGroupId = wrapGroupId,
                readingHint = readingHint
                    ?.takeIf { kanji && tokenStart == start && index == end }
            )
        }
    }

    if (links.isEmpty()) {
        addFallbackTokens(0, text.length)
        return tokens
    }

    var cursor = 0
    links.forEachIndexed { linkIndex, link ->
        val start = link.start.coerceIn(0, text.length)
        val end = link.end.coerceIn(start, text.length)
        if (start < cursor || start == end) return@forEachIndexed
        if (cursor < start) addFallbackTokens(cursor, start)
        val base = text.substring(start, end)
        addFallbackTokens(
            start = start,
            end = end,
            wordId = link.wordId.takeIf { it > 0 },
            wrapGroupId = linkIndex + 1,
            readingHint = link.kana.trim()
                .takeIf { it.isNotBlank() && base.all(::isRubyKanji) }
        )
        cursor = end
    }
    if (cursor < text.length) addFallbackTokens(cursor, text.length)

    return tokens
}

private fun alignRubyReadings(tokens: List<RubyToken>, kana: String): List<String?>? {
    if (kana.isBlank()) return tokens.map { null as String? }
    val memo = mutableMapOf<Pair<Int, Int>, List<String?>?>()

    fun solve(tokenIndex: Int, kanaIndex: Int): List<String?>? {
        val key = tokenIndex to kanaIndex
        if (key in memo) return memo[key]
        if (tokenIndex >= tokens.size) {
            val result: List<String?>? = if (kana.substring(kanaIndex.coerceAtMost(kana.length)).all { it.isWhitespace() }) {
                emptyList<String?>()
            } else {
                null
            }
            memo[key] = result
            return result
        }

        val token = tokens[tokenIndex]
        val result = if (token.kanji) {
            val hinted = token.readingHint
                ?.takeIf { it.isNotBlank() && rubyLiteralPrefixEquals(kana, kanaIndex, it) }
                ?.let { hint ->
                    solve(tokenIndex + 1, kanaIndex + hint.length)?.let { tail ->
                        listOf(hint) + tail
                    }
                }
            if (hinted != null) {
                memo[key] = hinted
                return hinted
            }
            var matched: List<String?>? = null
            for (end in kana.length downTo (kanaIndex + 1)) {
                val tail = solve(tokenIndex + 1, end) ?: continue
                matched = listOf(kana.substring(kanaIndex, end)) + tail
                break
            }
            matched
        } else {
            val consumed = consumeRubyLiteral(token.base, kana, kanaIndex)
            if (consumed >= 0) {
                solve(tokenIndex + 1, kanaIndex + consumed)?.let { listOf(null) + it }
            } else {
                null
            }
        }
        memo[key] = result
        return result
    }

    return solve(0, 0) ?: alignRubyReadingsGreedy(tokens, kana)
}

private fun alignRubyReadingsGreedy(tokens: List<RubyToken>, kana: String): List<String?> {
    val readings = MutableList<String?>(tokens.size) { null }
    var kanaIndex = 0
    tokens.forEachIndexed { index, token ->
        if (token.kanji) {
            val hint = token.readingHint
            if (!hint.isNullOrBlank() && rubyLiteralPrefixEquals(kana, kanaIndex, hint)) {
                readings[index] = hint
                kanaIndex += hint.length
                return@forEachIndexed
            }
            val nextLiteral = tokens.getOrNull(index + 1)
                ?.takeIf { !it.kanji }
                ?.base
                ?.filterNot(::rubyCanDropLiteral)
                ?.takeIf { it.isNotEmpty() }
            // Take the nearest occurrence of the following literal run, not the last one in the
            // sentence; otherwise one mismatch makes a single kanji swallow the rest of the reading.
            val end = if (nextLiteral == null) {
                kana.length
            } else {
                (kanaIndex + 1..kana.length - nextLiteral.length)
                    .firstOrNull { rubyLiteralPrefixEquals(kana, it, nextLiteral) }
                    ?: (kanaIndex + 1..kana.length - 1)
                        .firstOrNull { rubyLiteralEquals(nextLiteral[0], kana[it]) }
                    ?: kana.length
            }
            val safeEnd = end.coerceIn(kanaIndex, kana.length)
            readings[index] = kana.substring(kanaIndex, safeEnd)
            kanaIndex = safeEnd
        } else {
            val consumed = consumeRubyLiteral(token.base, kana, kanaIndex)
            kanaIndex = if (consumed >= 0) {
                kanaIndex + consumed
            } else {
                // Literal disagrees with the reading; assume one kana per char to stay in sync.
                (kanaIndex + token.base.count { !rubyCanDropLiteral(it) }).coerceAtMost(kana.length)
            }
        }
    }
    return readings
}

private fun consumeRubyLiteral(base: String, kana: String, start: Int): Int {
    var kanaIndex = start
    base.forEach { char ->
        when {
            char.isWhitespace() || rubyCanDropLiteral(char) -> Unit
            kanaIndex < kana.length && rubyLiteralEquals(char, kana[kanaIndex]) -> kanaIndex += 1
            else -> return -1
        }
    }
    return kanaIndex - start
}

private fun rubyLiteralPrefixEquals(text: String, start: Int, prefix: String): Boolean {
    if (start < 0 || start + prefix.length > text.length) return false
    return prefix.indices.all { offset ->
        rubyLiteralEquals(prefix[offset], text[start + offset])
    }
}

private fun isRubyKanji(char: Char): Boolean {
    return isJapaneseRubyKanjiLike(char)
}

private fun rubyLiteralEquals(textChar: Char, kanaChar: Char): Boolean {
    return japaneseRubyLiteralEquals(textChar, kanaChar)
}

private fun normalizeRubyLiteral(char: Char): Char {
    return when (char) {
        '.', '\u3002' -> '\u3002'
        ',', '\u3001' -> '\u3001'
        '!', '\uFF01' -> '\uFF01'
        '?', '\uFF1F' -> '\uFF1F'
        ':', '\uFF1A' -> '\uFF1A'
        ';', '\uFF1B' -> '\uFF1B'
        '(', '\uFF08' -> '\uFF08'
        ')', '\uFF09' -> '\uFF09'
        '\uFF70', '\u30FC' -> '\u30FC'
        else -> char
    }
}

private fun rubyCanDropLiteral(char: Char): Boolean {
    return canDropJapaneseRubyLiteral(char)
}

/** Opacity of the highlighter stroke behind the headword in example sentences. */
private const val HEADWORD_MARKER_ALPHA = 0.28f
