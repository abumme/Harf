package uz.abumme.harfgame.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UzbekTransliterationTest {

    @Test
    fun transliteratesByTheOfficialCorrespondence() {
        val cases = listOf(
            "kitob" to "китоб",
            "shahar" to "шаҳар", // sh → ш, h → ҳ
            "chiroq" to "чироқ", // ch → ч, q → қ
            "xalq" to "халқ", // x → х (distinct from h → ҳ)
            "oʻrdak" to "ўрдак",
            "gʻalla" to "ғалла",
            "yoʻl" to "йўл", // oʻ is one grapheme, so y stays й
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
        for ((latin, cyrillic) in cases) assertEquals(cyrillic, UzbekTransliteration.transliterate(latin), latin)
    }

    @Test
    fun inputIsNormalizedLikeAPlayersWord() {
        assertEquals("йўл", UzbekTransliteration.transliterateOrNull(" Yo'l "))
        assertEquals("шаҳар", UzbekTransliteration.transliterateOrNull("SHAHAR"))
    }

    @Test
    fun anythingButAnUzbekLatinWordHasNoSuggestion() {
        assertNull(UzbekTransliteration.transliterateOrNull("шаҳар"))
        assertNull(UzbekTransliteration.transliterateOrNull("crwth!"))
        assertNull(UzbekTransliteration.transliterateOrNull("   "))
        assertFailsWith<IllegalStateException> { UzbekTransliteration.transliterate("шаҳар") }
    }
}
