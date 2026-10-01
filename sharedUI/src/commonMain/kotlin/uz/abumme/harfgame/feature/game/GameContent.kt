package uz.abumme.harfgame.feature.game

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.game_suggest_word
import harf_game.sharedui.generated.resources.hard_mode_correct_position
import harf_game.sharedui.generated.resources.hard_mode_minimum_count
import harf_game.sharedui.generated.resources.hard_mode_missing_grapheme
import harf_game.sharedui.generated.resources.hard_mode_present_position
import harf_game.sharedui.generated.resources.not_enough_letters
import harf_game.sharedui.generated.resources.not_in_word_list
import harf_game.sharedui.generated.resources.suggest_failed
import harf_game.sharedui.generated.resources.suggest_sent
import org.jetbrains.compose.resources.stringResource
import uz.abumme.harfgame.engine.HardModeViolation
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.theme.LocalHarfColors

/**
 * The game screen's layout, free of DI so tests can pin it: a top bar, the board centered in the remaining
 * height, a fixed status strip and the keyboard slot — stacked on tall viewports, side by side on wide ones.
 * Every size comes from [GameLayout], so nothing moves while a round is played: feedback lives in the strip
 * and the round [result] takes the keyboard's slot.
 *
 * [leading] (back, hard-mode control), [center] (the Uzbek script switch) and [trailing] (help) fill the
 * top bar; they are slots because they need the screen's DI and navigation.
 */
@Composable
fun GameContent(
    state: GameState,
    config: LanguageConfig,
    strip: StripMessage?,
    onKey: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onSuggest: () -> Unit,
    leading: @Composable () -> Unit,
    center: (@Composable () -> Unit)?,
    trailing: @Composable () -> Unit,
    result: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHarfColors.current
    val shape = remember(config) { KeyboardShape.of(config) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(colors.paper)
            .padding(vertical = GameLayout.PAD_V.dp),
    ) {
        val wide = maxWidth > maxHeight
        val plan = if (wide) {
            GameLayout.wide(maxWidth.value, maxHeight.value, shape, state.tileCount, state.maxAttempts)
        } else {
            GameLayout.portrait(maxWidth.value, maxHeight.value, shape, state.tileCount, state.maxAttempts)
        }
        val playing = state.status == GameStatus.Playing

        val topBar: @Composable () -> Unit = { TopBar(leading, center, trailing) }
        val board: @Composable ColumnScope.() -> Unit = {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = GameLayout.BOARD_PAD_H.dp),
                contentAlignment = Alignment.Center,
            ) {
                BoardView(state, Modifier.testTag("board"), tileSize = plan.tile.dp)
            }
        }
        val stripView: @Composable () -> Unit = {
            StatusStrip(strip, onSuggest, Modifier.fillMaxWidth().height(GameLayout.STRIP.dp))
        }
        // The keyboard's slot keeps the keyboard's height after the round ends, so the board never moves;
        // result content taller than the slot scrolls inside it instead of pushing the board.
        val slot: @Composable () -> Unit = {
            val slotModifier = Modifier.fillMaxWidth().height(plan.keyboardHeight.dp)
            if (playing) {
                Box(slotModifier, contentAlignment = Alignment.Center) {
                    KeyboardView(config, state.keyStates, onKey, onDelete, onEnter, keyHeight = plan.keyHeight.dp)
                }
            } else {
                Box(slotModifier.verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().heightIn(min = plan.keyboardHeight.dp), contentAlignment = Alignment.Center) {
                        result()
                    }
                }
            }
        }

        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = GameLayout.WIDE_PAD_H.dp),
                horizontalArrangement = Arrangement.spacedBy(GameLayout.WIDE_GAP.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GameLayout.GAP.dp)) {
                    topBar()
                    board()
                    stripView()
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { slot() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GameLayout.GAP.dp)) {
                topBar()
                board()
                stripView()
                slot()
            }
        }
    }
}

@Composable
private fun TopBar(leading: @Composable () -> Unit, center: (@Composable () -> Unit)?, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(GameLayout.TOP_BAR.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { leading() }
        if (center != null) center() else Spacer(Modifier)
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** One line of feedback in a fixed-height strip; the suggest-to-add action is an inline link. */
@Composable
private fun StatusStrip(message: StripMessage?, onSuggest: () -> Unit, modifier: Modifier) {
    val colors = LocalHarfColors.current
    Box(modifier.testTag("strip"), contentAlignment = Alignment.Center) {
        Crossfade(targetState = message, animationSpec = tween(150), label = "strip") { m ->
            if (m != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stripText(m),
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The message yields to the action: on a narrow phone it is the message that ellipsizes.
                        modifier = Modifier.weight(1f, fill = false).testTag("strip-message"),
                    )
                    if (m is StripMessage.UnknownWord) {
                        Text("·", color = colors.muted, fontSize = 13.sp)
                        Text(
                            stringResource(Res.string.game_suggest_word),
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("strip-action").clickable(enabled = !m.sending, onClick = onSuggest),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stripText(message: StripMessage): String = when (message) {
    StripMessage.NotEnoughLetters -> stringResource(Res.string.not_enough_letters)
    is StripMessage.UnknownWord -> stringResource(Res.string.not_in_word_list)
    is StripMessage.HardMode -> hardModeText(message.violation)
    StripMessage.SuggestionSent -> stringResource(Res.string.suggest_sent)
    StripMessage.SuggestionFailed -> stringResource(Res.string.suggest_failed)
}

@Composable
private fun hardModeText(v: HardModeViolation): String = when (v) {
    is HardModeViolation.CorrectPositionChanged ->
        stringResource(Res.string.hard_mode_correct_position, v.position + 1, v.expected.uppercase())
    is HardModeViolation.PresentGraphemeAtSamePosition ->
        stringResource(Res.string.hard_mode_present_position, v.grapheme.uppercase(), v.position + 1)
    is HardModeViolation.MinimumCountNotSatisfied ->
        if (v.requiredCount <= 1) {
            stringResource(Res.string.hard_mode_missing_grapheme, v.grapheme.uppercase())
        } else {
            stringResource(Res.string.hard_mode_minimum_count, v.requiredCount, v.grapheme.uppercase())
        }
}
