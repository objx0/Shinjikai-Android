package com.shinjikai.dictionary.data

/**
 * Arabic-side search for the offline dictionary, tuned to reproduce shinjikai.app's ordering.
 *
 * How the website ranks an Arabic query (reverse-engineered from its results):
 * - Only the first line of each of the first [INDEXED_MEANINGS] meanings is searched. Meaning lines that
 *   start with a Japanese usage tag ("[な～] …") or a note ("$…") are skipped, as is text after ':' or '$'
 *   and anything inside parentheses.
 * - The first meaning that contains every query word decides the score:
 *   exact phrase → difficulty + 4 − meaningIndex,
 *   word inside a phrase → difficulty − 6 × meaningIndex − 2 × (extra words in the first such phrase).
 * - Ties: JLPT, then id.
 * - Letters are unified only for diacritics, tatweel, alef forms and ة/ه; ى, ؤ and ئ stay distinct.
 *
 * Entries the website would not return (late meanings, notes, ى/ي spelling variants, partial words) are
 * still found, but always rank after every website-style match.
 *
 * Stored format ([buildSearchText]): one line per indexed meaning, phrases separated by " | " (a phrase
 * that lost words to a broken parenthesis carries "~<wordCount>"), optionally a tab and the meaning's
 * searchable words when they differ from the phrase words; then a "#" line with loosely normalized
 * extra words for the fallback tiers.
 */
internal object ArabicSearch {
    const val INDEXED_MEANINGS = 5

    private const val EXACT_BONUS = 4
    private const val EXACT_MEANING_PENALTY = 1
    private const val WORD_MEANING_PENALTY = 6
    private const val EXTRA_WORD_PENALTY = 2
    private const val UNKNOWN_PHRASE_WORDS = 9

    private const val PHRASE_SEPARATOR = " | "
    private const val MEANING_SEPARATOR = "\n"
    private const val WORDS_SEPARATOR = '\t'
    private const val WORD_COUNT_MARK = '~'
    private const val EXTRA_MARKER = "\n#\n"

    private val DIACRITICS_REGEX = Regex("""[ً-ٰٟۖ-ۭ]""")
    private val ALEF_VARIANTS_REGEX = Regex("""[أإآٱ]""")
    private val NON_LETTER_NUMBER_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val MARKUP_REGEX = Regex("""\{[^{}]*\}""")
    private val PIPE_PAREN_NOTE_REGEX = Regex("""\(\|[^|]*\|\)""")
    private val PIPE_NOTE_REGEX = Regex("""\|[^|]*\|""")
    private val PARENTHETICAL_REGEX = Regex("""\([^()]*\)""")
    private val PHRASE_SPLIT_REGEX = Regex("""[،,؛;/\[\]]""")

    fun isArabicQuery(text: String): Boolean =
        text.any { ch -> ch in '؀'..'ۿ' || ch in 'ݐ'..'ݿ' }

    /** Website-compatible normalization. */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(DIACRITICS_REGEX, "")
            .replace("ـ", "")
            .replace(ALEF_VARIANTS_REGEX, "ا")
            .replace('ة', 'ه')
            .replace(NON_LETTER_NUMBER_SPACE_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /** Looser normalization for the fallback tiers, so common spelling variants still find something. */
    fun normalizeLoose(text: String): String =
        normalize(text).replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')

    fun buildSearchText(meanings: List<String>): String {
        val lines = meanings.take(INDEXED_MEANINGS).map(::encodeMeaning)
        val primaryWords = lines.asSequence()
            .flatMap { MeaningIndex.parse(it).words.asSequence() }
            .toHashSet()
        // Loose spellings (ى→ي …) of primary words are kept here too, so the FTS index can find them.
        val extraWords = meanings.asSequence()
            .flatMap { normalizeLoose(it).split(' ').asSequence() }
            .filter { it.isNotBlank() && it !in primaryWords }
            .distinct()
            .toList()
        val primary = lines.joinToString(MEANING_SEPARATOR)
        return if (extraWords.isEmpty()) primary else primary + EXTRA_MARKER + extraWords.joinToString(" ")
    }

    /** Builds a searchable index for rows imported before [buildSearchText] existed. */
    fun indexFromGlossary(glossary: String): Index =
        Index.parse(buildSearchText(glossary.split(" / ")))

    private fun encodeMeaning(meaning: String): String {
        var line = meaning.substringBefore('\n')
        if (line.startsWith('$')) return ""
        if (line.length > 1 && line[0] == '[' && isJapanese(line[1])) return ""
        line = line.substringBefore('$')
            .replace(MARKUP_REGEX, " ")
            .replace(PIPE_PAREN_NOTE_REGEX, " ")
            .replace(PIPE_NOTE_REGEX, " ")
        val head = line.substringBefore(':')

        val phrases = head.split(PHRASE_SPLIT_REGEX).mapNotNull { raw ->
            val visible = normalize(removeUnbalancedParens(raw))
            if (visible.isEmpty()) return@mapNotNull null
            val fullWordCount = wordCount(normalize(removeParens(raw)))
            if (fullWordCount == wordCount(visible)) visible else "$visible$WORD_COUNT_MARK$fullWordCount"
        }
        val encodedPhrases = phrases.joinToString(PHRASE_SEPARATOR)
        val searchableWords = normalize(removeParens(head)).split(' ').filter { it.isNotEmpty() }.toSet()
        val phraseWords = MeaningIndex.parse(encodedPhrases).words
        return if (searchableWords == phraseWords) {
            encodedPhrases
        } else {
            encodedPhrases + WORDS_SEPARATOR + searchableWords.joinToString(" ")
        }
    }

    private fun removeParens(text: String): String {
        var current = text
        while (true) {
            val next = current.replace(PARENTHETICAL_REGEX, " ")
            if (next == current) return current
            current = next
        }
    }

    /** Drops balanced parentheses, then whatever a parenthesis split by a comma left behind. */
    private fun removeUnbalancedParens(text: String): String {
        var result = removeParens(text)
        if ('(' in result) result = result.substringBefore('(')
        if (')' in result) result = result.substringAfterLast(')')
        return result
    }

    private fun wordCount(text: String): Int = if (text.isBlank()) 0 else text.split(' ').size

    private fun isJapanese(ch: Char): Boolean = ch in '぀'..'ヿ' || ch in '㐀'..'鿿'

    class Query(raw: String) {
        val normalized: String = normalize(raw)
        val tokens: List<String> = normalized.split(' ').filter { it.isNotEmpty() }
        val looseTokens: List<String> = tokens.map(::normalizeLoose)
        val isEmpty: Boolean get() = tokens.isEmpty()
    }

    /**
     * Result of matching one entry. [tier] 0 is a website-style match ordered by [score] (higher is better);
     * tiers 1..3 are fallbacks (loose whole word, word prefix, substring) ordered by difficulty.
     */
    data class Match(val tier: Int, val score: Int)

    private class Phrase(val text: String, val wordCount: Int) {
        val words: List<String> = text.split(' ')
    }

    private class MeaningIndex(val phrases: List<Phrase>, val words: Set<String>) {
        companion object {
            fun parse(line: String): MeaningIndex {
                val phrasePart = line.substringBefore(WORDS_SEPARATOR)
                val phrases = phrasePart.split(PHRASE_SEPARATOR)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { encoded ->
                        val text = encoded.substringBefore(WORD_COUNT_MARK)
                        val count = encoded.substringAfter(WORD_COUNT_MARK, "").toIntOrNull()
                        Phrase(text, count ?: text.split(' ').size)
                    }
                val words = if (WORDS_SEPARATOR in line) {
                    line.substringAfter(WORDS_SEPARATOR).split(' ').filter { it.isNotEmpty() }.toSet()
                } else {
                    phrases.flatMapTo(HashSet()) { it.words }
                }
                return MeaningIndex(phrases, words)
            }
        }
    }

    class Index private constructor(
        private val meanings: List<MeaningIndex>,
        private val extraWords: List<String>
    ) {
        private val looseWords: Set<String> by lazy {
            buildSet {
                meanings.forEach { meaning -> meaning.words.forEach { add(normalizeLoose(it)) } }
                addAll(extraWords)
            }
        }

        /** Returns null when the entry does not contain the query at all. */
        fun match(query: Query, difficulty: Int): Match? {
            if (query.isEmpty) return null
            val tokens = query.tokens
            meanings.forEachIndexed { meaningIndex, meaning ->
                if (meaningIndex >= INDEXED_MEANINGS || !tokens.all { it in meaning.words }) return@forEachIndexed
                val exact = meaning.phrases.any { it.text == query.normalized && it.wordCount == tokens.size }
                if (exact) {
                    return Match(0, difficulty + EXACT_BONUS - EXACT_MEANING_PENALTY * meaningIndex)
                }
                val phraseWords = meaning.phrases
                    .firstOrNull { phrase -> tokens.all { it in phrase.words } }
                    ?.wordCount
                    ?: UNKNOWN_PHRASE_WORDS
                val extraWords = (phraseWords - tokens.size).coerceAtLeast(0)
                return Match(
                    0,
                    difficulty - WORD_MEANING_PENALTY * meaningIndex - EXTRA_WORD_PENALTY * extraWords
                )
            }
            val loose = query.looseTokens
            return when {
                loose.all { it in looseWords } -> Match(1, 0)
                loose.all { token -> looseWords.any { it.startsWith(token) } } -> Match(2, 0)
                loose.all { token -> looseWords.any { it.contains(token) } } -> Match(3, 0)
                else -> null
            }
        }

        companion object {
            fun parse(searchText: String): Index {
                val primary = searchText.substringBefore(EXTRA_MARKER)
                val extra = searchText.substringAfter(EXTRA_MARKER, missingDelimiterValue = "")
                return Index(
                    meanings = primary.split(MEANING_SEPARATOR).map(MeaningIndex::parse),
                    extraWords = extra.split(' ').filter { it.isNotBlank() }
                )
            }
        }
    }
}
