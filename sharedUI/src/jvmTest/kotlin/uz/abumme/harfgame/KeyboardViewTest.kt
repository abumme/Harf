package uz.abumme.harfgame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import uz.abumme.harfgame.feature.game.KeyboardView
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.theme.HarfTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The on-screen keyboard's structure is fixed per language: letter rows, then one action row with ENTER
 * leading and ⌫ trailing — on every width. Checked through the semantics tree, which is what a player's
 * screen reader sees too.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardViewTest {

    private fun ComposeUiTest.bounds(label: String): DpRect = onNodeWithText(label).getBoundsInRoot()

    private fun ComposeUiTest.keyboard(config: LanguageConfig, width: Dp) {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                Box(Modifier.width(width)) { KeyboardView(config, emptyMap(), {}, {}, {}) }
            }
        }
    }

    private fun sameRow(a: DpRect, b: DpRect) = abs((a.top - b.top).value) < 0.5f

    @Test
    fun enter_and_delete_share_an_action_row_below_the_letters() = runDesktopComposeUiTest {
        // 412 dp: wide enough that the old layout put ⌫ inline in the top row
        keyboard(LaunchLanguages.uzCyrl, 412.dp)
        val enter = bounds("ENTER")
        val delete = bounds("⌫")
        assertTrue(sameRow(enter, delete), "ENTER and ⌫ sit in the same row")
        for (letter in listOf("Й", "Ъ", "Ф", "Ё", "Я", "Ҳ")) {
            assertTrue(bounds(letter).bottom <= enter.top, "$letter is above the action row")
        }
    }

    @Test
    fun enter_leads_and_delete_trails_the_keyboard() = runDesktopComposeUiTest {
        keyboard(LaunchLanguages.ru, 360.dp)
        val enter = bounds("ENTER")
        val delete = bounds("⌫")
        // the 12-key top row spans the keyboard; the action keys reach its outer edges
        assertEquals(bounds("Й").left.value, enter.left.value, 0.5f, "ENTER starts where the top row starts")
        assertEquals(bounds("Ъ").right.value, delete.right.value, 0.5f, "⌫ ends where the top row ends")
        assertTrue(enter.right < delete.left, "ENTER is left of ⌫")
    }

    @Test
    fun uzbek_latin_hosts_apostrophe_graphemes_between_enter_and_delete() = runDesktopComposeUiTest {
        keyboard(LaunchLanguages.uzLatn, 360.dp)
        val enter = bounds("ENTER")
        val delete = bounds("⌫")
        for (key in listOf("Oʻ", "Gʻ")) {
            val b = bounds(key)
            assertTrue(sameRow(b, enter), "$key is in the action row")
            assertTrue(b.left >= enter.right && b.right <= delete.left, "$key sits between ENTER and ⌫")
        }
        assertTrue(bounds("SH").bottom <= enter.top, "digraph keys stay in the letter rows")
    }

    @Test
    fun rows_do_not_change_between_a_narrow_and_a_wide_phone() = runDesktopComposeUiTest {
        var width by mutableStateOf(360.dp)
        setContent {
            HarfTheme(paletteId = "newsprint") {
                Box(Modifier.width(width)) { KeyboardView(LaunchLanguages.uzCyrl, emptyMap(), {}, {}, {}) }
            }
        }
        val labels = LaunchLanguages.uzCyrl.keyboard.flatten().map { it.uppercase() } + listOf("ENTER", "⌫")
        fun rowsByTop(): Map<String, Int> {
            val tops = labels.map { bounds(it).top.value }.distinct().sorted()
            return labels.associateWith { label -> tops.indexOfFirst { abs(it - bounds(label).top.value) < 0.5f } }
        }
        val narrow = rowsByTop()
        width = 412.dp
        waitForIdle()
        val wide = rowsByTop()
        assertEquals(narrow, wide, "every key stays in the same row when the phone gets wider")
        assertEquals(3, narrow.getValue("ENTER"), "the action row is the fourth row")
    }
}
