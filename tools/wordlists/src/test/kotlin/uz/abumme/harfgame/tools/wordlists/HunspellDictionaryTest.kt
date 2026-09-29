package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HunspellDictionaryTest {

    private fun resource(name: String): Path = Path.of(javaClass.getResource("/hunspell/$name")!!.toURI())

    // Mirrors the real kk_KZ files: UTF-8 with a byte-order mark, suffix rules conditioned on the stem's ending.
    private val kazakh = HunspellDictionary(resource("kk.aff"), resource("kk.dic"))

    @Test
    fun acceptsDictionaryWordsAndTheirAffixedForms() {
        assertTrue(kazakh.accepts("қала"))
        assertTrue(kazakh.accepts("балалар"))
        assertTrue(kazakh.accepts("орал"))
    }

    @Test
    fun rejectsFormsTheAffixRulesDoNotAllow() {
        assertFalse(kazakh.accepts("қаладар"), "дар needs a stem ending in a vowel and л")
        assertFalse(kazakh.accepts("оралдар"), "орал has no affix flags")
        assertFalse(kazakh.accepts("жаңбыр"), "not in the dictionary")
    }
}
