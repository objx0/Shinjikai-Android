package com.shinjikai.dictionary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArabicSearchTest {
    private fun match(query: String, vararg meanings: String, difficulty: Int = 3): ArabicSearch.Match? =
        ArabicSearch.Index
            .parse(ArabicSearch.buildSearchText(meanings.toList()))
            .match(ArabicSearch.Query(query), difficulty)

    @Test
    fun `normalize strips diacritics but keeps alef maqsura distinct like the website`() {
        assertEquals("الشرطه", ArabicSearch.normalize("الشُرْطَة"))
        assertEquals("ايمان", ArabicSearch.normalize("إِيمان"))
        assertEquals("غنى", ArabicSearch.normalize("غِنى"))
        assertEquals("غني", ArabicSearch.normalizeLoose("غِنى"))
    }

    @Test
    fun `score rewards exact phrases, early meanings and short phrases`() {
        assertEquals(ArabicSearch.Match(0, 3 + 4), match("ماء", "ماء، (ج) مياه"))
        assertEquals(ArabicSearch.Match(0, 3 + 4 - 1), match("ماء", "سائل", "ماء"))
        assertEquals(ArabicSearch.Match(0, 3 - 2), match("ماء", "ماء ساخن"))
        assertEquals(ArabicSearch.Match(0, 3 - 6 - 2), match("ماء", "وجه", "ماء الوجه"))
    }

    @Test
    fun `notes are handled the way the website handles them`() {
        // (|…|) notes are dropped before splitting, so this is still an exact phrase.
        assertEquals(ArabicSearch.Match(0, 7), match("رجل", "رَجُل (|بالغ، راشد|)، (ج) رِجال"))
        // A plain parenthesis split by a comma leaves a longer phrase, not an exact one.
        assertEquals(ArabicSearch.Match(0, 3 - 4), match("يقرأ", "يقرأ (المقياس المدرج، شريط القياس الخ)"))
        // Bracket labels split phrases.
        assertEquals(ArabicSearch.Match(0, 7), match("قديم", "[قديم] يا له (|من كذا|)"))
    }

    @Test
    fun `text the website ignores is still found but only in fallback tiers`() {
        assertEquals(1, match("المقياس", "يقرأ (المقياس المدرج، شريط القياس الخ)")?.tier)
        assertEquals(1, match("غني", "[な～] غني، ثري")?.tier)
        assertEquals(1, match("يكتب", "كتابة، تدوين\n[する～] يكتب")?.tier)
        assertEquals(1, match("مكاتب", "كوبان : مكاتب صغيرة")?.tier)
        assertEquals(1, match("نار", "أ", "ب", "ج", "د", "هـ", "نار")?.tier)
        assertEquals(1, match("غني", "ثروة، غِنى")?.tier)
        assertEquals(ArabicSearch.Match(0, 7), match("غنى", "ثروة، غِنى"))
        assertEquals(2, match("ماء", "ماءات")?.tier)
        assertNull(match("نار", "ماء"))
    }

    @Test
    fun `legacy glossary rows are indexed on the fly`() {
        assertEquals(
            ArabicSearch.Match(0, 4),
            ArabicSearch.indexFromGlossary("إيمان").match(ArabicSearch.Query("إِيمان"), 0)
        )
    }
}
