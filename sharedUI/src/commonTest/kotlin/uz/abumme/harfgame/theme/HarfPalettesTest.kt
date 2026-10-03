package uz.abumme.harfgame.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class HarfPalettesTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun assertContrast(edition: String, role: String, fg: Color, bg: Color, atLeast: Float) {
        val ratio = contrast(fg, bg)
        assertTrue(ratio >= atLeast, "$edition: $role contrast $ratio < $atLeast")
    }

    @Test
    fun every_edition_has_a_legible_dark_variant() {
        for (p in HarfPalettes.all) {
            val d = p.dark
            val id = "${p.id}/dark"
            assertTrue(d.paper.luminance() < 0.05f && p.colors.paper.luminance() > 0.5f, "$id: ground is not dark")
            assertContrast(id, "ink/paper", d.ink, d.paper, 7f)
            assertContrast(id, "ink/card", d.ink, d.card, 7f)
            assertContrast(id, "muted/paper", d.muted, d.paper, 4.5f)
            assertContrast(id, "accent/paper", d.accent, d.paper, 4.5f)
            assertContrast(id, "danger/paper", d.danger, d.paper, 4.5f)
            assertContrast(id, "success/paper", d.success, d.paper, 4.5f)
            // marks are graphics, not body text: 3:1
            assertContrast(id, "absent/paper", d.absent.compositeOver(d.paper), d.paper, 3f)
        }
    }

    @Test
    fun letters_over_filled_marks_are_legible_on_both_grounds() {
        for (p in HarfPalettes.all) {
            for ((ground, c) in listOf("light" to p.colors, "dark" to p.dark)) {
                val id = "${p.id}/$ground"
                // tile glyphs are large bold text: 3:1
                assertContrast(id, "onCorrect/correct", c.onCorrect, c.correct, 3f)
                assertContrast(id, "onPresent/present", c.onPresent, c.present, 3f)
                assertContrast(id, "onAbsent/absent", c.onAbsent, c.absent.compositeOver(c.paper), 3f)
            }
        }
    }
}
