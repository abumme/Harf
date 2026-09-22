package uz.abumme.harfgame.feature

import uz.abumme.harfgame.engine.Mark.ABSENT
import uz.abumme.harfgame.engine.Mark.CORRECT
import uz.abumme.harfgame.engine.Mark.PRESENT
import uz.abumme.harfgame.feature.result.ShareGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareGridTest {

    private val rows = listOf(
        listOf(ABSENT, PRESENT, ABSENT, ABSENT, ABSENT),
        listOf(CORRECT, CORRECT, CORRECT, CORRECT, CORRECT),
    )

    @Test
    fun grid_has_header_plus_one_line_per_row() {
        val text = ShareGrid.build("newsprint", "English", 312L, rows, won = true)
        val lines = text.split("\n")
        assertEquals(1 + rows.size, lines.size)
        assertTrue(lines[0].startsWith("Harf · English · №312"))
        assertTrue(lines[0].contains("2/6"), "won in 2 attempts")
    }

    @Test
    fun loss_shows_X_score() {
        val text = ShareGrid.build("newsprint", "English", 312L, rows, won = false)
        assertTrue(text.split("\n")[0].contains("X/6"))
    }

    @Test
    fun palette_maps_to_non_wordle_squares() {
        assertEquals("🟦", ShareGrid.emoji("newsprint", CORRECT))
        assertEquals("🟥", ShareGrid.emoji("newsprint", PRESENT))
        assertEquals("⬜", ShareGrid.emoji("newsprint", ABSENT))
        assertEquals("🟩", ShareGrid.emoji("schoolbook", CORRECT)) // green here is a pencil green, not Wordle's flat tile
    }

    @Test
    fun ordinary_daily_share_retains_existing_header_behavior() {
        val text = ShareGrid.build("newsprint", "English", 312L, rows, won = true, isArchive = false, hardMode = false)
        val header = text.split("\n")[0]
        assertEquals("Harf · English · №312  2/6", header)
    }

    @Test
    fun hard_mode_share_marks_asterisk_in_header() {
        val text = ShareGrid.build("newsprint", "English", 312L, rows, won = true, isArchive = false, hardMode = true)
        val header = text.split("\n")[0]
        assertEquals("Harf · English · №312  2/6*", header)
    }

    @Test
    fun archive_share_distinguishes_from_today_with_archive_prefix() {
        val text = ShareGrid.build("newsprint", "Русский", 150L, rows, won = true, isArchive = true, hardMode = false)
        val header = text.split("\n")[0]
        assertEquals("Harf Archive · Русский · №150  2/6", header)
    }

    @Test
    fun archive_and_hard_mode_share_combines_markers() {
        val text = ShareGrid.build("newsprint", "Русский", 150L, rows, won = false, isArchive = true, hardMode = true)
        val header = text.split("\n")[0]
        assertEquals("Harf Archive · Русский · №150  X/6*", header)
    }
}
