package uz.abumme.harfgame.tools.wordlists

import kotlin.test.Test
import kotlin.test.assertEquals

class UzbekCyrillicTest {

    @Test
    fun transliteratesByTheOfficialCorrespondence() {
        val cases = listOf(
            "kitob" to "китоб",
            "shahar" to "шаҳар", // sh → ш, h → ҳ
            "chiroq" to "чироқ", // ch → ч, q → қ
            "xalq" to "халқ", // x → х (distinct from h → ҳ)
            "oʻrdak" to "ўрдак",
            "gʻalla" to "ғалла",
            "yangi" to "янги", // ya → я, ng → нг
            "yuz" to "юз",
            "dunyo" to "дунё",
            "tayyor" to "тайёр", // y before a consonant → й, then yo → ё
            "oyoq" to "оёқ",
            "yer" to "ер", // word-initial ye → е
            "poyezd" to "поезд", // ye after a vowel → е
            "ekin" to "экин", // word-initial e → э
            "poeziya" to "поэзия", // e after a vowel → э
        )
        for ((latin, cyrillic) in cases) assertEquals(cyrillic, UzbekCyrillic.transliterate(latin), latin)
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
