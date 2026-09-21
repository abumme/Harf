package uz.abumme.harfgame.backend.admin.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlocklistsTest {

    @Test
    fun entriesAndWordsAreComparedInTheLanguagesNormalizedForm() {
        val lists = mapOf(
            "ru" to listOf("# comment", "", "  Ёжик  "),
            "uz-latn" to listOf("o'g'ri"),
            "en" to listOf("Quilt"),
        )
        val blocklists = Blocklists(lines = { lists[it].orEmpty() })

        assertTrue(blocklists.isBlocked("ru", "ежик"))
        assertTrue(blocklists.isBlocked("ru", "ЁЖИК"))
        assertTrue(blocklists.isBlocked("uz-latn", "oʻgʻri"))
        assertTrue(blocklists.isBlocked("uz-latn", "O‘G’RI"))
        assertTrue(blocklists.isBlocked("en", " QUILT "))
        assertFalse(blocklists.isBlocked("en", "crane"))
        assertFalse(blocklists.isBlocked("kk", "ежик"))
        assertEquals(setOf("ежик"), blocklists.of("ru"))
    }

    @Test
    fun eachLanguageIsLoadedOnce() {
        val loads = mutableListOf<String>()
        val blocklists = Blocklists(lines = { lang -> loads += lang; listOf("quilt") })
        repeat(3) { blocklists.isBlocked("en", "quilt") }
        assertEquals(listOf("en"), loads)
    }

    @Test
    fun theDeployedBlocklistsAreRead() {
        assertTrue(Blocklists().isBlocked("en", "Shit"))
    }
}
