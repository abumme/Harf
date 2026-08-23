package uz.abumme.harfgame.engine

import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenizerTest {

    private val uzLatn = Tokenizer(LaunchLanguages.uzLatn)
    private val ru = Tokenizer(LaunchLanguages.ru)
    private val en = Tokenizer(LaunchLanguages.en)

    @Test
    fun digraphs_count_as_one_tile() {
        assertEquals(listOf("sh", "a", "h", "a", "r"), uzLatn.tokenize("shahar"))
    }

    @Test
    fun single_char_language_is_one_to_one() {
        assertEquals(listOf("b", "r", "e", "a", "d"), en.tokenize("bread"))
        assertEquals(listOf("к", "н", "и", "г", "а"), ru.tokenize("книга"))
    }

    @Test
    fun longest_match_wins_ng_over_n() {
        // singil -> s i ng i l (ng is one grapheme)
        assertEquals(listOf("s", "i", "ng", "i", "l"), uzLatn.tokenize("singil"))
    }

    @Test
    fun exception_overrides_false_digraph() {
        // ustunga: -ga postposition, n+g NOT the digraph ng
        assertEquals(listOf("u", "s", "t", "u", "n", "g", "a"), uzLatn.tokenize("ustunga"))
    }

    @Test
    fun apostrophe_variants_normalize_to_tutuq() {
        val canonical = uzLatn.tokenize("oʻzbek")
        assertEquals(listOf("oʻ", "z", "b", "e", "k"), canonical)
        // typewriter apostrophe and backtick fold to the same decomposition
        assertEquals(canonical, uzLatn.tokenize("o'zbek"))
        assertEquals(canonical, uzLatn.tokenize("o`zbek"))
    }

    @Test
    fun russian_yo_folds_to_ye() {
        // ё normalized to е, in both lowercase and uppercase input
        val expected = listOf("е", "л", "к", "а")
        assertEquals(expected, ru.tokenize("елка"))
        assertEquals(expected, ru.tokenize("ёлка"))
        assertEquals(expected, ru.tokenize("Ёлка"))
    }

    @Test
    fun unknown_character_returns_null() {
        assertNull(en.tokenize("café")) // é not in inventory
    }
}
