package com.shinjikai.dictionary

import com.shinjikai.dictionary.data.SentenceWordLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RubySentenceTextTest {
    @Test
    fun `sentence ruby treats kanji iteration mark as kanji-like`() {
        val segments = buildRubySegments(
            text = "\u6642\u3005",
            kana = "\u3068\u304D\u3069\u304D",
            links = emptyList()
        )

        assertEquals(
            listOf(
                RubySegment(
                    base = "\u6642\u3005",
                    ruby = "\u3068\u304D\u3069\u304D",
                    start = 0,
                    end = 2,
                    wordId = null
                )
            ),
            segments
        )
    }

    @Test
    fun `linked mixed words keep one wrap group while ruby stays on kanji`() {
        val segments = buildRubySegments(
            text = "\u304A\u91D1\u3092\u9810\u3051\u305F",
            kana = "\u304A\u304B\u306D\u3092\u3042\u305A\u3051\u305F",
            links = listOf(
                SentenceWordLink(
                    start = 0,
                    end = 2,
                    wordId = 10,
                    text = "\u304A\u91D1",
                    kana = "\u304A\u304B\u306D"
                ),
                SentenceWordLink(
                    start = 3,
                    end = 6,
                    wordId = 20,
                    text = "\u9810\u3051\u305F",
                    kana = "\u3042\u305A\u3051\u305F"
                )
            )
        )

        assertEquals(
            listOf(
                RubySegment(base = "\u304A", ruby = null, start = 0, end = 1, wordId = 10, wrapGroupId = 1),
                RubySegment(base = "\u91D1", ruby = "\u304B\u306D", start = 1, end = 2, wordId = 10, wrapGroupId = 1),
                RubySegment(base = "\u3092", ruby = null, start = 2, end = 3, wordId = null),
                RubySegment(base = "\u9810", ruby = "\u3042\u305A", start = 3, end = 4, wordId = 20, wrapGroupId = 2),
                RubySegment(base = "\u3051\u305F", ruby = null, start = 4, end = 6, wordId = 20, wrapGroupId = 2)
            ),
            segments
        )
    }

    @Test
    fun `all kanji linked words keep their link reading hint`() {
        val segments = buildRubySegments(
            text = "\u5E78\u798F",
            kana = "\u3053\u3046\u3075\u304F",
            links = listOf(
                SentenceWordLink(
                    start = 0,
                    end = 2,
                    wordId = 30,
                    text = "\u5E78\u798F",
                    kana = "\u3053\u3046\u3075\u304F"
                )
            )
        )

        assertEquals(
            listOf(
                RubySegment(
                    base = "\u5E78\u798F",
                    ruby = "\u3053\u3046\u3075\u304F",
                    start = 0,
                    end = 2,
                    wordId = 30,
                    wrapGroupId = 1
                )
            ),
            segments
        )
    }

    @Test
    fun `tokens from the same linked word share a non null wrap group`() {
        val segments = buildRubySegments(
            text = "\u9810\u3051\u308B",
            kana = "\u3042\u305A\u3051\u308B",
            links = listOf(
                SentenceWordLink(
                    start = 0,
                    end = 3,
                    wordId = 20,
                    text = "\u9810\u3051\u308B",
                    kana = "\u3042\u305A\u3051\u308B"
                )
            )
        )

        assertEquals(2, segments.size)
        assertNotNull(segments[0].wrapGroupId)
        assertEquals(segments[0].wrapGroupId, segments[1].wrapGroupId)
        assertEquals(listOf(20, 20), segments.map { it.wordId })
    }

    @Test
    fun `longer overlapping link wins when links start at the same offset`() {
        val segments = buildRubySegments(
            text = "\u304A\u91D1",
            kana = "\u304A\u304B\u306D",
            links = listOf(
                SentenceWordLink(
                    start = 0,
                    end = 1,
                    wordId = 1,
                    text = "\u304A",
                    kana = "\u304A"
                ),
                SentenceWordLink(
                    start = 0,
                    end = 2,
                    wordId = 2,
                    text = "\u304A\u91D1",
                    kana = "\u304A\u304B\u306D"
                )
            )
        )

        assertEquals(listOf(2, 2), segments.map { it.wordId })
        assertEquals(listOf(0 to 1, 1 to 2), segments.map { it.start to it.end })
        assertEquals(segments[0].wrapGroupId, segments[1].wrapGroupId)
    }

    @Test
    fun `ascii punctuation in kana aligns with japanese punctuation in text`() {
        val segments = buildRubySegments(
            text = "\u884C\u304F\u3002",
            kana = "\u3044\u304F.",
            links = emptyList()
        )

        assertEquals(
            listOf(
                RubySegment(base = "\u884C", ruby = "\u3044", start = 0, end = 1, wordId = null),
                RubySegment(base = "\u304F\u3002", ruby = null, start = 1, end = 3, wordId = null)
            ),
            segments
        )
    }

    @Test
    fun `katakana in sentence aligns against hiragana kana`() {
        val segments = buildRubySegments(
            text = "警察がデモ隊を散乱させるために、催涙ガスを用いました。",
            kana = "けいさつがでもたいをさんらんさせるために、さいるいがすをもちいました。",
            links = emptyList()
        )
        val rubies = segments.filter { it.ruby != null }.associate { it.base to it.ruby }

        assertEquals(
            mapOf(
                "警察" to "けいさつ",
                "隊" to "たい",
                "散乱" to "さんらん",
                "催涙" to "さいるい",
                "用" to "もち"
            ),
            rubies
        )
    }

    @Test
    fun `katakana long vowel mark matches spelled-out vowel`() {
        val segments = buildRubySegments(
            text = "コーヒーを飲む",
            kana = "こうひいをのむ",
            links = emptyList()
        )
        assertEquals("の", segments.first { it.base == "飲" }.ruby)
    }
}
