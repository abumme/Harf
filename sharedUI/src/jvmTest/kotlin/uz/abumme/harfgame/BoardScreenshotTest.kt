package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.engine.HardModeViolation
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.Mark.ABSENT
import uz.abumme.harfgame.engine.Mark.CORRECT
import uz.abumme.harfgame.engine.Mark.PRESENT
import uz.abumme.harfgame.feature.game.BoardView
import uz.abumme.harfgame.feature.game.GameContent
import uz.abumme.harfgame.feature.game.GameRow
import uz.abumme.harfgame.feature.game.GameState
import uz.abumme.harfgame.feature.game.KeyboardView
import uz.abumme.harfgame.feature.game.StripMessage
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.test.Test

/**
 * Board + keyboard goldens: one component frame, then the whole game layout on the phone sizes the
 * design was pinned on (`docs/ui-review/game-layout.html`), so a layout regression on any of them shows
 * up as a golden diff. Goldens are canonical as rendered on CI — re-record from the CI artifact.
 */
@OptIn(ExperimentalTestApi::class)
class BoardScreenshotTest {

    private fun state(languageId: String, rows: List<Pair<List<String>, List<Mark>>>, current: List<String>) = GameState(
        languageId = languageId,
        tileCount = 5,
        submitted = rows.map { (graphemes, marks) -> GameRow(graphemes, marks) },
        current = current,
        keyStates = buildMap {
            val rank = mapOf(ABSENT to 1, PRESENT to 2, CORRECT to 3)
            for ((graphemes, marks) in rows) for ((g, m) in graphemes.zip(marks)) {
                if ((rank.getValue(m)) > (get(g)?.let(rank::getValue) ?: 0)) put(g, m)
            }
        },
    )

    private val uzLatn = state(
        "uz-latn",
        listOf(
            listOf("k", "i", "t", "o", "b") to listOf(ABSENT, PRESENT, ABSENT, CORRECT, ABSENT),
            listOf("sh", "a", "h", "a", "r") to listOf(CORRECT, PRESENT, ABSENT, CORRECT, ABSENT),
        ),
        current = listOf("ch", "a"),
    )
    private val uzCyrl = state(
        "uz-cyrl",
        listOf(
            listOf("к", "и", "т", "о", "б") to listOf(ABSENT, PRESENT, ABSENT, CORRECT, ABSENT),
            listOf("ш", "а", "ҳ", "а", "р") to listOf(CORRECT, PRESENT, ABSENT, CORRECT, ABSENT),
        ),
        current = listOf("ч", "а"),
    )
    private val en = state(
        "en",
        listOf(
            listOf("c", "r", "a", "n", "e") to listOf(ABSENT, PRESENT, ABSENT, ABSENT, PRESENT),
            listOf("s", "l", "a", "t", "e") to listOf(CORRECT, ABSENT, CORRECT, ABSENT, PRESENT),
        ),
        current = listOf("s", "t"),
    )

    private fun frame(name: String, width: Int, height: Int, config: LanguageConfig, state: GameState, strip: StripMessage? = null) =
        runDesktopComposeUiTest {
            setContent {
                HarfTheme(paletteId = "newsprint") {
                    Box(Modifier.size(width.dp, height.dp)) {
                        GameContent(
                            state = state,
                            config = config,
                            strip = strip,
                            onKey = {}, onDelete = {}, onEnter = {}, onSuggest = {},
                            leading = { Text("‹") },
                            center = if (config.id.startsWith("uz")) ({ Text(config.scriptLabel) }) else null,
                            trailing = { Text("?") },
                            result = { Text("result") },
                        )
                    }
                }
            }
            onRoot().captureRoboImage("roborazzi/$name.png")
        }

    @Test
    fun board_and_keyboard_uzbek_latin() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Column(
                    modifier = Modifier.width(360.dp).background(c.paper).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BoardView(uzLatn)
                    KeyboardView(LaunchLanguages.uzLatn, uzLatn.keyStates, {}, {}, {})
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/game_board_uz_latn.png")
    }

    @Test
    fun game_360x640() {
        frame("game_360x640_uz_latn", 360, 640, LaunchLanguages.uzLatn, uzLatn, StripMessage.UnknownWord("chaxyz"))
        frame("game_360x640_uz_cyrl", 360, 640, LaunchLanguages.uzCyrl, uzCyrl)
        frame("game_360x640_en", 360, 640, LaunchLanguages.en, en)
    }

    @Test
    fun game_393x852() {
        frame("game_393x852_uz_latn", 393, 852, LaunchLanguages.uzLatn, uzLatn, StripMessage.NotEnoughLetters)
        frame("game_393x852_uz_cyrl", 393, 852, LaunchLanguages.uzCyrl, uzCyrl)
        frame(
            "game_393x852_en", 393, 852, LaunchLanguages.en, en,
            StripMessage.HardMode(HardModeViolation.CorrectPositionChanged(position = 0, expected = "s", actual = "c")),
        )
    }

    @Test
    fun game_412x915() {
        frame("game_412x915_uz_latn", 412, 915, LaunchLanguages.uzLatn, uzLatn)
        frame("game_412x915_uz_cyrl", 412, 915, LaunchLanguages.uzCyrl, uzCyrl)
        frame("game_412x915_en", 412, 915, LaunchLanguages.en, en)
    }

    @Test
    fun game_landscape_915x412() {
        frame("game_915x412_uz_latn", 915, 412, LaunchLanguages.uzLatn, uzLatn)
    }
}
