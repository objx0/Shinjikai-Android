package com.shinjikai.dictionary

internal fun isJapaneseRubyKanjiLike(char: Char): Boolean {
    return isJapaneseRubyKanjiLikeCodePoint(char.code)
}

internal fun isJapaneseRubyKanjiLikeCodePoint(codePoint: Int): Boolean {
    val block = Character.UnicodeBlock.of(codePoint)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        codePoint == 0x3005 ||
        codePoint == 0x3006 ||
        codePoint == 0x30F6
}

internal fun japaneseRubyLiteralEquals(textChar: Char, readingChar: Char): Boolean {
    if (textChar == readingChar) return true
    val text = foldKatakanaToHiragana(normalizeJapaneseRubyLiteral(textChar))
    val reading = foldKatakanaToHiragana(normalizeJapaneseRubyLiteral(readingChar))
    if (text == reading) return true
    // Katakana long-vowel mark may be spelled out as a vowel in hiragana readings (コーヒー → こうひい).
    return text == '\u30FC' && reading in LONG_VOWEL_READINGS
}

private const val LONG_VOWEL_READINGS = "\u3042\u3044\u3046\u3048\u304A\u3041\u3043\u3045\u3047\u3049"

/** Maps katakana ァ..ヶ and ヽヾ to their hiragana counterparts; leaves everything else untouched. */
internal fun foldKatakanaToHiragana(char: Char): Char {
    return when (char) {
        in '\u30A1'..'\u30F6', '\u30FD', '\u30FE' -> (char.code - 0x60).toChar()
        else -> char
    }
}

internal fun normalizeJapaneseRubyLiteral(char: Char): Char {
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

internal fun canDropJapaneseRubyLiteral(char: Char): Boolean {
    return char.isWhitespace() || char == '"' || char == '\'' || char == '\u201C' || char == '\u201D'
}
