package uz.abumme.harfgame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import uz.abumme.harfgame.engine.Mark.ABSENT
import uz.abumme.harfgame.engine.Mark.CORRECT
import uz.abumme.harfgame.engine.Mark.PRESENT
import uz.abumme.harfgame.feature.game.GameContent
import uz.abumme.harfgame.feature.game.GameLayout
import uz.abumme.harfgame.feature.game.GameRow
import uz.abumme.harfgame.feature.game.GameState
import uz.abumme.harfgame.feature.game.GameStatus
import uz.abumme.harfgame.feature.game.StripMessage
import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.theme.HarfTheme
import kotlin.test.Test
import java.util.Locale
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The game screen's layout contract: the board and the keyboard never move while a round is played —
 * not when feedback appears in the status strip, not when the suggest offer shows, not when the round ends.
 */
@OptIn(ExperimentalTestApi::class)
class GameContentTest {

    private val playing = GameState(
        languageId = "uz-latn",
        tileCount = 5,
        submitted = listOf(GameRow(listOf("k", "i", "t", "o", "b"), listOf(ABSENT, PRESENT, ABSENT, CORRECT, ABSENT))),
        current = listOf("ch", "a"),
    )
    private val won = playing.copy(
        status = GameStatus.Won,
        current = emptyList(),
        submitted = playing.submitted + GameRow(listOf("sh", "a", "h", "a", "r"), List(5) { CORRECT }),
    )

    private fun ComposeUiTest.content(width: Int, height: Int, state: () -> GameState, strip: () -> StripMessage?) {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                Box(Modifier.size(width.dp, height.dp)) {
                    GameContent(
                        state = state(),
                        config = LaunchLanguages.uzLatn,
                        strip = strip(),
                        onKey = {}, onDelete = {}, onEnter = {}, onSuggest = {},
                        leading = { Text("BACK") },
                        center = { Text("SCRIPT") },
                        trailing = { Text("HELP") },
                        result = { Text("RESULT") },
                    )
                }
            }
        }
    }

    @Test
    fun board_and_keyboard_stay_put_through_feedback_and_the_round_end() = runDesktopComposeUiTest {
        var state by mutableStateOf(playing)
        var strip by mutableStateOf<StripMessage?>(null)
        content(393, 852, { state }, { strip })

        val board = onNodeWithTag("board").getBoundsInRoot()
        val enter = onNodeWithText("ENTER").getBoundsInRoot()
        val stripBounds = onNodeWithTag("strip").getBoundsInRoot()
        assertEquals(GameLayout.STRIP, (stripBounds.bottom - stripBounds.top).value, 0.5f, "the strip is always its fixed height")

        strip = StripMessage.UnknownWord("chaxyz")
        waitForIdle()
        onNodeWithTag("strip-message").assertExists()
        onNodeWithTag("strip-action").assertExists()
        assertEquals(board, onNodeWithTag("board").getBoundsInRoot(), "the suggest offer did not move the board")
        assertEquals(enter, onNodeWithText("ENTER").getBoundsInRoot(), "the suggest offer did not move the keyboard")
        assertEquals(stripBounds, onNodeWithTag("strip").getBoundsInRoot())

        strip = StripMessage.NotEnoughLetters
        waitForIdle()
        onNodeWithTag("strip-action").assertDoesNotExist()
        assertEquals(board, onNodeWithTag("board").getBoundsInRoot())

        state = won
        strip = null
        waitForIdle()
        assertEquals(board, onNodeWithTag("board").getBoundsInRoot(), "the round end did not move the board")
        onNodeWithText("ENTER").assertDoesNotExist()
        val result = onNodeWithText("RESULT").getBoundsInRoot()
        // the slot spans the four keyboard rows; ENTER was the last one
        val keyH = (enter.bottom - enter.top).value
        val slotTop = enter.top.value - 3 * (keyH + GameLayout.ROW_GAP)
        assertTrue(result.top.value >= slotTop && result.bottom <= enter.bottom, "the result sits inside the keyboard's slot")
    }

    @Test
    fun keyboard_is_anchored_to_the_bottom_and_the_board_fills_the_rest() = runDesktopComposeUiTest {
        content(360, 640, { playing }, { null })
        val enter = onNodeWithText("ENTER").getBoundsInRoot()
        assertEquals(640f - GameLayout.PAD_V, enter.bottom.value, 0.5f, "the action row ends at the bottom padding")
        val board = onNodeWithTag("board").getBoundsInRoot()
        // 360×640 with no window insets: the plan gives 48 dp keys and ~50 dp tiles
        val plan = GameLayout.portrait(360f, 640f - 2 * GameLayout.PAD_V, uz.abumme.harfgame.feature.game.KeyboardShape.of(LaunchLanguages.uzLatn), 5)
        assertEquals(plan.boardHeight, (board.bottom - board.top).value, 1f, "board height follows the plan")
        assertEquals(48f, (enter.bottom - enter.top).value, 0.5f)
    }

    @Test
    fun suggest_link_is_never_cut_on_a_narrow_phone() {
        // Every UI language, since widths are the text's: Russian's long link makes the message ellipsize, English's
        // short one fits beside it. Whatever the language, the action shows whole on one line inside the strip.
        val original = Locale.getDefault()
        val links = mutableSetOf<String>()
        try {
            for (language in listOf("en", "ru", "uz")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                runDesktopComposeUiTest {
                    content(300, 568, { playing }, { StripMessage.UnknownWord("chaxyz") })
                    val message = onNodeWithTag("strip-message").getBoundsInRoot()
                    val action = onNodeWithTag("strip-action").getBoundsInRoot()
                    val link = textLayout("strip-action")
                    links += link.layoutInput.text.text
                    assertEquals(1, link.lineCount, "$language: the action stays on one line")
                    // Not hasVisualOverflow: with softWrap off the paragraph is laid out wider than the box even when
                    // the whole text fits. What matters: no ellipsis, and the box is as wide as the text needs.
                    assertFalse(link.isLineEllipsized(0), "$language: the action is not ellipsized")
                    assertTrue(
                        link.size.width >= ceil(link.multiParagraph.maxIntrinsicWidth),
                        "$language: the action keeps its full width (${link.size.width} of ${link.multiParagraph.maxIntrinsicWidth})",
                    )
                    assertTrue(action.right <= 284.dp, "$language: action inside the strip padding")
                    assertTrue(message.right <= action.left, "$language: the message yields to the action")
                }
            }
        } finally {
            Locale.setDefault(original)
        }
        assertEquals(3, links.size, "each language rendered its own link: $links")
    }

    private fun ComposeUiTest.textLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    @Test
    fun wide_window_puts_the_keyboard_beside_the_board() = runDesktopComposeUiTest {
        content(915, 412, { playing }, { null })
        val board = onNodeWithTag("board").getBoundsInRoot()
        val enter = onNodeWithText("ENTER").getBoundsInRoot()
        assertTrue(board.right <= enter.left, "board is left of the keyboard")
        assertTrue(board.bottom <= 412.dp && enter.bottom <= 412.dp, "nothing overflows the window")
    }
}
