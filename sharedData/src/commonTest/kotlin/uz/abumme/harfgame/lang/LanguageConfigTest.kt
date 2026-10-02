package uz.abumme.harfgame.lang

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageConfigTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun config_serializes_round_trip() {
        val original = LaunchLanguages.uzLatn
        val restored = json.decodeFromString<LanguageConfig>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun every_config_exposes_inventory_and_keyboard() {
        for ((id, config) in LaunchLanguages.all) {
            assertTrue(config.graphemes.isNotEmpty(), "$id has graphemes")
            assertTrue(config.keyboard.isNotEmpty(), "$id has a keyboard")
            // every keyboard key is a real grapheme of the language
            val keys = (config.keyboard.flatten() + config.actionRowKeys).toSet()
            assertTrue(config.graphemes.toSet().containsAll(keys), "$id keyboard keys are all graphemes")
            // the UI hosts action-row graphemes next to enter/delete; a letter row never repeats them
            assertTrue(config.keyboard.flatten().none { it in config.actionRowKeys }, "$id action-row keys are not letter-row keys")
        }
    }

    @Test
    fun uzbek_latin_hosts_apostrophe_graphemes_in_the_action_row() {
        val config = LaunchLanguages.uzLatn
        assertEquals(3, config.keyboard.size, "three letter rows, like every other launch language")
        assertEquals(listOf("oʻ", "gʻ"), config.actionRowKeys)
    }

    @Test
    fun other_languages_have_no_action_row_keys() {
        for (id in listOf("uz-cyrl", "ru", "en", "kk")) {
            assertTrue(LaunchLanguages.all.getValue(id).actionRowKeys.isEmpty(), "$id has no action-row keys")
        }
    }

    @Test
    fun uzbek_latin_has_a_key_per_digraph() {
        val keys = (LaunchLanguages.uzLatn.keyboard.flatten() + LaunchLanguages.uzLatn.actionRowKeys).toSet()
        assertTrue(keys.containsAll(LaunchLanguages.uzLatn.digraphs))
        assertEquals(setOf("sh", "ch", "ng", "oʻ", "gʻ"), LaunchLanguages.uzLatn.digraphs.toSet())
    }

    @Test
    fun other_scripts_have_no_digraphs() {
        for (id in listOf("uz-cyrl", "ru", "en", "kk")) {
            assertTrue(LaunchLanguages.all.getValue(id).digraphs.isEmpty(), "$id has no digraphs")
        }
    }
}
