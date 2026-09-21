package uz.abumme.harfgame.tools.wordlists

import kotlin.test.Test
import kotlin.test.assertEquals

// The transliteration rules themselves are covered by :sharedData's UzbekTransliterationTest.
class UzbekCyrillicTest {

    @Test
    fun usesTheSharedTransliteration() {
        assertEquals("шаҳар", UzbekCyrillic.transliterate("shahar"))
        assertEquals("йўл", UzbekCyrillic.transliterate("yoʻl"))
    }

    @Test
    fun cyrillicWordsAreWiktionarysCyrillicPlusEveryLatinWordRecheckedForTheBoard() {
        val rawCandidates = listOf(
            "kitob", // 5 Latin tiles → китоб, 5 Cyrillic
            "китоб", // Wiktionary's own Cyrillic entry for the same word
            "шаҳар", // a Cyrillic-only entry
            "qayiq", // → қайиқ
            "o'rdak", // apostrophe variant → oʻrdak → ўрдак
            "tanga", // 4 Latin tiles (ng) → танга, 5 Cyrillic: kept
            "dengiz", // 5 Latin tiles → денгиз, 6 Cyrillic: dropped
            "yashil", // 5 Latin tiles → яшил, 4 Cyrillic: dropped
        )
        assertEquals(setOf("китоб", "шаҳар", "қайиқ", "ўрдак", "танга"), UzbekCyrillic.words(rawCandidates))
    }
}
