package uz.abumme.harfgame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import uz.abumme.harfgame.feature.settings.MarkStyleSection
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Settings' mark-style section: the three style rows plus the legend, so the choice is explained in place. */
@OptIn(ExperimentalTestApi::class)
class MarkStyleSectionTest {

    @Test
    fun legend_is_shown_under_the_style_rows() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                Box(Modifier.width(360.dp)) { MarkStyleSection(active = HarfMarkStyleId.Scribble, onSelect = {}) }
            }
        }
        onNodeWithTag("mark-legend").assertExists()
        val legend = onNodeWithTag("mark-legend").getBoundsInRoot()
        for (style in HarfMarkStyleId.entries) {
            assertTrue(onNodeWithText(style.name).getBoundsInRoot().bottom <= legend.top, "${style.name} row is above the legend")
        }
    }

    @Test
    fun tapping_a_row_selects_that_style() = runDesktopComposeUiTest {
        var selected: HarfMarkStyleId? = null
        setContent {
            HarfTheme(paletteId = "newsprint") {
                Box(Modifier.width(360.dp)) { MarkStyleSection(active = HarfMarkStyleId.Scribble, onSelect = { selected = it }) }
            }
        }
        onNodeWithText(HarfMarkStyleId.Fill.name).performClick()
        assertEquals(HarfMarkStyleId.Fill, selected)
    }
}
