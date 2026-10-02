package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The keyboard layouts checked against the launch languages' grapheme inventories. */
class KeyboardLayoutsTest {

    @Test
    fun every_launch_language_has_exactly_one_layout() {
        assertEquals(LaunchLanguages.all.keys, KeyboardLayouts.all.keys)
    }

    @Test
    fun every_key_is_a_grapheme() {
        for ((id, shape) in KeyboardLayouts.all) {
            val graphemes = LaunchLanguages.all.getValue(id).graphemes.toSet()
            assertTrue(graphemes.containsAll(shape.keys), "$id keyboard keys are all graphemes")
        }
    }

    @Test
    fun action_row_keys_are_not_letter_row_keys() {
        // the UI hosts action-row graphemes next to enter/delete; a letter row never repeats them
        for ((id, shape) in KeyboardLayouts.all) {
            assertTrue(shape.letterRows.flatten().none { it in shape.actionRowKeys }, "$id action-row keys are not letter-row keys")
        }
    }

    @Test
    fun uzbek_latin_hosts_apostrophe_graphemes_in_the_action_row() {
        val shape = KeyboardLayouts.all.getValue(LaunchLanguages.uzLatn.id)
        assertEquals(3, shape.letterRows.size, "three letter rows, like every other launch language")
        assertEquals(listOf("oʻ", "gʻ"), shape.actionRowKeys)
    }

    @Test
    fun other_languages_have_no_action_row_keys() {
        for (id in listOf("uz-cyrl", "ru", "en", "kk")) {
            assertTrue(KeyboardLayouts.all.getValue(id).actionRowKeys.isEmpty(), "$id has no action-row keys")
        }
    }

    @Test
    fun uzbek_latin_has_a_key_per_digraph() {
        val keys = KeyboardLayouts.all.getValue(LaunchLanguages.uzLatn.id).keys
        assertTrue(keys.containsAll(LaunchLanguages.uzLatn.digraphs))
    }
}
