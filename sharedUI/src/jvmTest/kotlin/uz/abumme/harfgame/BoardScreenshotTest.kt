package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.engine.Mark.ABSENT
import uz.abumme.harfgame.engine.Mark.CORRECT
import uz.abumme.harfgame.engine.Mark.PRESENT
import uz.abumme.harfgame.feature.game.BoardView
import uz.abumme.harfgame.feature.game.GameRow
import uz.abumme.harfgame.feature.game.GameState
import uz.abumme.harfgame.feature.game.KeyboardView
import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BoardScreenshotTest {

    private val state = GameState(
        languageId = "uz-latn",
        tileCount = 5,
        submitted = listOf(
            GameRow(listOf("k", "i", "t", "o", "b"), listOf(ABSENT, PRESENT, ABSENT, CORRECT, ABSENT)),
            GameRow(listOf("sh", "a", "h", "a", "r"), listOf(CORRECT, CORRECT, CORRECT, CORRECT, CORRECT)),
        ),
        current = listOf("ch", "a"),
        keyStates = mapOf("sh" to CORRECT, "a" to CORRECT, "i" to PRESENT, "t" to ABSENT),
    )

    @Test
    fun board_and_keyboard_uzbek_latin() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Column(
                    modifier = Modifier.width(360.dp).background(c.paper).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BoardView(state)
                    KeyboardView(LaunchLanguages.uzLatn, state.keyStates, {}, {}, {})
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/game_board_uz_latn.png")
    }

    @Test
    fun board_and_keyboard_landscape() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                // Wide/side-by-side arrangement: board (scaled down) left, keyboard right.
                Row(
                    modifier = Modifier.size(640.dp, 360.dp).background(c.paper).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        BoardView(state, tileSize = 40.dp)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        KeyboardView(LaunchLanguages.uzLatn, state.keyStates, {}, {}, {})
                    }
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/game_board_landscape.png")
    }
}
