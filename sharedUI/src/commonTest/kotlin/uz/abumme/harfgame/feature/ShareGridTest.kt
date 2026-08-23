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
}
