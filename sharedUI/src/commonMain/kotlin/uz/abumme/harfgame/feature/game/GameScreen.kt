package uz.abumme.harfgame.feature.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_copy
import harf_game.sharedui.generated.resources.action_back
import harf_game.sharedui.generated.resources.action_got_it
import harf_game.sharedui.generated.resources.action_share
import harf_game.sharedui.generated.resources.game_hard_mode
import harf_game.sharedui.generated.resources.game_suggest_word
import harf_game.sharedui.generated.resources.hard_mode_correct_position
import harf_game.sharedui.generated.resources.hard_mode_minimum_count
import harf_game.sharedui.generated.resources.hard_mode_missing_grapheme
import harf_game.sharedui.generated.resources.hard_mode_present_position
import harf_game.sharedui.generated.resources.help
import harf_game.sharedui.generated.resources.howto_body
import harf_game.sharedui.generated.resources.howto_title
import harf_game.sharedui.generated.resources.not_enough_letters
import harf_game.sharedui.generated.resources.not_in_word_list
import harf_game.sharedui.generated.resources.result_out_of_tries
import harf_game.sharedui.generated.resources.result_solved
import harf_game.sharedui.generated.resources.settings_support_harf
import harf_game.sharedui.generated.resources.suggest_failed
import harf_game.sharedui.generated.resources.suggest_sent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.feature.onboarding.MarkLegend
import uz.abumme.harfgame.feature.result.ShareGrid
import uz.abumme.harfgame.feature.share.Sharer
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfColors
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.LocalMarkStyle
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun GameScreen(languageId: String, onBack: () -> Unit = {}, onPaywall: () -> Unit = {}) {
    val provider = koinInject<DailyPuzzleProvider>()
    val packs = koinInject<WordPackRepository>()
    val registry = koinInject<LanguageRegistry>()
    val resultLog = koinInject<ResultLog>()
    val roundStore = koinInject<RoundStore>()
    val syncManager = koinInject<uz.abumme.harfgame.data.stats.SyncManager>()
    val gamesServices = koinInject<uz.abumme.harfgame.games.GamesServices>()
    val scope = rememberCoroutineScope()

    // The round lives in the Game entry, outside composition: a return from the paywall finds it ready, and an
    // Uzbek script switch keeps the current board until the other script's round is ready.
    val loader = viewModel {
        GameLoadViewModel { s ->
            val puzzle = provider.daily(s)
            Loaded(puzzle, registry.config(s)!!, packs.load(s), roundStore.load(s, puzzle.epochDay))
        }.also { it.select(languageId) }
    }
    val loaded by loader.loaded.collectAsState()
    val selectedScript by loader.selected.collectAsState()

    val data = loaded ?: run {
        // Not ready yet: still a real page, so the transition moves the game's frame rather than an empty one.
        GameSkeleton(onBack)
        return
    }
    val (puzzle, config, pack, restore) = data
    // The script on the board (Uzbek can switch latn <-> cyrl for the same daily lexeme).
    val script = puzzle.languageId

    // Keyed by the round, not the script: switching there and back must build the VM from the converted round
    // instead of reusing the stale one from the first visit. A paywall round trip keeps the same round, so the same VM.
    val vm = viewModel(key = "$script#${data.generation}") {
        GameViewModel(puzzle, pack, restore) { record ->
            scope.launch {
                resultLog.record(record)
                // B11: keep the finished round persisted so reopening today shows the result,
                // not a blank editable board. The snapshot is saved by the effect below.
                syncManager.pushStats()
                // Best-effort Play Games update (Android only; no-op elsewhere or when signed out).
                gamesServices.submitProgress(
                    uz.abumme.harfgame.games.GamesProgress.from(resultLog.all(), registry.ids)
                )
            }
        }
    }
    val state by vm.state.collectAsState()
    // Set while a script switch is converting the round: input then would be lost with the board it was typed on.
    var switching by remember(data.generation) { mutableStateOf(false) }
    val act: (GameAction) -> Unit = { if (!switching) vm.onAction(it) }
    val colors = LocalHarfColors.current
    var showHelp by remember { mutableStateOf(false) }

    // transient feedback for rejected submissions (too short / not in dictionary)
    var message by remember { mutableStateOf<String?>(null) }
    var hardViolation by remember { mutableStateOf<uz.abumme.harfgame.engine.HardModeViolation?>(null) }
    // the just-rejected unknown word, offered for suggestion until the player edits or sends it
    var suggestCandidate by remember { mutableStateOf<String?>(null) }
    var suggestBusy by remember { mutableStateOf(false) }
    val incompleteMsg = stringResource(Res.string.not_enough_letters)
    val invalidMsg = stringResource(Res.string.not_in_word_list)
    val suggestSentMsg = stringResource(Res.string.suggest_sent)
    val suggestFailedMsg = stringResource(Res.string.suggest_failed)

    val hardViolationMsg = hardViolation?.let { v ->
        when (v) {
            is uz.abumme.harfgame.engine.HardModeViolation.CorrectPositionChanged ->
                stringResource(Res.string.hard_mode_correct_position, v.position + 1, v.expected.uppercase())
            is uz.abumme.harfgame.engine.HardModeViolation.PresentGraphemeAtSamePosition ->
                stringResource(Res.string.hard_mode_present_position, v.grapheme.uppercase(), v.position + 1)
            is uz.abumme.harfgame.engine.HardModeViolation.MinimumCountNotSatisfied ->
                if (v.requiredCount <= 1) {
                    stringResource(Res.string.hard_mode_missing_grapheme, v.grapheme.uppercase())
                } else {
                    stringResource(Res.string.hard_mode_minimum_count, v.requiredCount, v.grapheme.uppercase())
                }
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                GameEvent.Incomplete -> message = incompleteMsg
                GameEvent.InvalidGuess -> {
                    message = invalidMsg
                    suggestCandidate = vm.state.value.current.joinToString("")
                }
                is GameEvent.HardModeViolation -> hardViolation = ev.violation
                is GameEvent.RoundEnded -> {}
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(1500.milliseconds); message = null }
    }
    LaunchedEffect(hardViolation) {
        if (hardViolation != null) {
            delay(1500.milliseconds); hardViolation = null }
    }
    // A new/edited guess invalidates the pending suggestion offer.
    LaunchedEffect(state.current) {
        if (suggestCandidate != null && state.current.joinToString("") != suggestCandidate) {
            suggestCandidate = null
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(Res.string.action_got_it)) } },
            title = { Text(stringResource(Res.string.howto_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.howto_body), color = colors.ink)
                    MarkLegend()
                }
            },
        )
    }

    // persist the round as it changes — including the finished state, so today's result is
    // restored on relaunch (a stale prior-day round is ignored by RoundStore.load's day check).
    LaunchedEffect(script, state.submitted.size, state.current.size, state.status) {
        roundStore.save(vm.snapshot())
    }

    // Physical-keyboard support (desktop, tablets, Play Games on PC): map hardware keys to the same
    // game input. A typed char maps to a single-grapheme key of the current language; multi-char
    // graphemes (Uzbek sh/ch/oʻ/gʻ/ng) have no single physical key and stay on-screen only.
    val focusRequester = remember { FocusRequester() }
    val keyLookup = remember(config) { config.keyboard.flatten().associateBy { it.lowercase() } }
    LaunchedEffect(script) { runCatching { focusRequester.requestFocus() } }

    // Reusable pieces so the tall and wide layouts share one source of truth.
    val helpRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackControl(onBack)
            // Hard Mode toggle (pre-round only, gated by Founder entitlement)
            if (state.submitted.isEmpty() && state.status == GameStatus.Playing) {
                val isFounder = uz.abumme.harfgame.billing.EntitlementGate.lifetimeExtrasUnlocked(
                    koinInject<uz.abumme.harfgame.billing.EntitlementRepository>().entitlements.collectAsState().value
                )
                val hardShape = uz.abumme.harfgame.theme.LocalHarfShapes.current.button
                if (isFounder) {
                    val on = state.hardMode
                    Box(
                        modifier = Modifier
                            .clip(hardShape)
                            .background(if (on) colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                            .border(1.dp, if (on) colors.accent else colors.rule, hardShape)
                            .clickable { act(GameAction.ToggleHardMode) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            stringResource(Res.string.game_hard_mode),
                            color = if (on) colors.accent else colors.muted,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(hardShape)
                            .border(1.dp, colors.rule, hardShape)
                            .clickable(onClick = onPaywall)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            stringResource(Res.string.game_hard_mode) + " 🔒",
                            color = colors.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else if (state.hardMode) {
                Text(
                    stringResource(Res.string.game_hard_mode),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
          }

            HelpControl(onClick = { showHelp = true })
        }
    }
    val switchScript: (String) -> Unit = { targetScript ->
        if (targetScript != selectedScript) {
            switching = true
            val currentSnapshot = vm.snapshot()
            scope.launch {
                try {
                    roundStore.save(currentSnapshot)
                    val targetPuzzle = provider.daily(targetScript)
                    val converted = uz.abumme.harfgame.lang.UzbekScriptConverter.convertRound(
                        round = currentSnapshot,
                        targetScript = targetScript,
                        targetAnswer = targetPuzzle.answer,
                    )
                    roundStore.save(converted)
                    loader.select(targetScript)
                } catch (e: Exception) {
                    switching = false // the switch did not happen; keep playing this board
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }
        }
    }
    val chips: @Composable () -> Unit = {
        if (languageId.startsWith("uz")) {
            uz.abumme.harfgame.theme.SegmentedSwitch(
                options = listOf("uz-latn" to "Lotin", "uz-cyrl" to "Кирилл"),
                selectedKey = selectedScript ?: script,
                onSelect = { switchScript(it) },
            )
        }
    }
    val feedback: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkLegend(Modifier.padding(vertical = 2.dp), compact = true)
            val displayMsg = hardViolationMsg ?: message
            Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                displayMsg?.let { Text(it, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
            // Offer to suggest an unknown full-length word for editor review.
            if (state.status == GameStatus.Playing && suggestCandidate != null) {
                val candidate = suggestCandidate!!
                TextButton(
                    enabled = !suggestBusy,
                    onClick = {
                        scope.launch {
                            suggestBusy = true
                            val result = syncManager.suggestWord(script, candidate)
                            message = if (result is uz.abumme.harfgame.data.api.ApiResult.Success) suggestSentMsg else suggestFailedMsg
                            suggestCandidate = null
                            suggestBusy = false
                        }
                    },
                ) {
                    Text(stringResource(Res.string.game_suggest_word), color = colors.accent, fontSize = 13.sp)
                }
            }
        }
    }
    val playArea: @Composable () -> Unit = {
        if (state.status == GameStatus.Playing) {
            KeyboardView(
                config = config,
                keyStates = state.keyStates,
                onKey = { act(GameAction.Input(it)) },
                onDelete = { act(GameAction.Delete) },
                onEnter = { act(GameAction.Submit) },
            )
        } else {
            ResultView(state, config.displayName, puzzle.epochDay, onPaywall)
        }
    }
    // Board sized to fully fit whatever space it is given; capped at 64.dp so the board grows to
    // use spare height on tall phones instead of leaving a large empty margin.
    val board: @Composable (maxW: Dp, maxH: Dp) -> Unit = { maxW, maxH ->
        val gap = 6.dp
        val ts = minOf(
            64.dp,
            (maxW - gap * (state.tileCount - 1)) / state.tileCount,
            (maxH - gap * (state.maxAttempts - 1)) / state.maxAttempts,
        )
        BoardView(state, tileSize = ts)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || state.status != GameStatus.Playing) {
                    return@onPreviewKeyEvent false
                }
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter -> { act(GameAction.Submit); true }
                    Key.Backspace -> { act(GameAction.Delete); true }
                    else -> {
                        val key = ev.utf16CodePoint.takeIf { it != 0 }
                            ?.let { keyLookup[it.toChar().toString().lowercase()] }
                        if (key != null) { act(GameAction.Input(key)); true } else false
                    }
                }
            }
            .focusable(),
    ) {
        val wide = maxWidth > maxHeight
        if (wide) {
            // Board and keyboard side by side, each using half the width and the full height.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    helpRow(); chips()
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        board(maxWidth, maxHeight)
                    }
                    feedback()
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { playArea() }
            }
        } else {
            // Vertical stack; the board fills the space left between the chrome and the keyboard.
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                helpRow(); chips()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    board(maxWidth, maxHeight)
                }
                feedback()
                playArea()
            }
        }
    }
}

/** The header's back control; the skeleton draws the same one, so it does not move when the round arrives. */
@Composable
private fun BackControl(onBack: () -> Unit) {
    val backLabel = stringResource(Res.string.action_back)
    TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) {
        Text("‹", color = LocalHarfColors.current.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HelpControl(onClick: () -> Unit) {
    val helpLabel = stringResource(Res.string.help)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = helpLabel }) {
        Text("?", color = LocalHarfColors.current.muted)
    }
}

/**
 * The game page before its round is ready: the same insets, padding and header row as the loaded screen (back works;
 * help waits for the round), so the transition moves a real page and the header stays put when the board appears.
 */
@Composable
private fun GameSkeleton(onBack: () -> Unit) {
    val header: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BackControl(onBack)
                Spacer(Modifier.width(1.dp))
            }
            HelpControl(onClick = {})
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) { header() }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) { header() }
        }
    }
}

/**
 * A contrasting halo behind a tile/key glyph so it stays legible over a filled mark — a light
 * halo under a dark letter, a dark halo under a light one. Returns a plain style (no shadow) when
 * there is no mark, so unmarked cells are unaffected.
 */
private fun glyphHalo(mark: Mark?, letterColor: Color, colors: HarfColors, blurPx: Float): TextStyle {
    if (mark == null) return TextStyle.Default
    val halo = if (letterColor.luminance() < 0.5f) colors.paper else colors.ink
    return TextStyle(shadow = Shadow(color = halo, blurRadius = blurPx))
}

@Composable
fun BoardView(state: GameState, modifier: Modifier = Modifier, tileSize: Dp = 46.dp) {
    val colors = LocalHarfColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in 0 until state.maxAttempts) {
            val row = state.submitted.getOrNull(r)
            val isCurrent = r == state.submitted.size && state.status == GameStatus.Playing
            // First empty cell of the in-progress row is where the next grapheme lands.
            // current.size == tileCount when the row is full, so no cell is active then.
            val activeCol = if (isCurrent) state.current.size else -1
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until state.tileCount) {
                    val g = row?.graphemes?.getOrNull(c) ?: if (isCurrent) state.current.getOrNull(c) else null
                    Tile(g, row?.marks?.getOrNull(c), colors, tileSize, active = c == activeCol)
                }
            }
        }
    }
}

@Composable
private fun Tile(grapheme: String?, mark: Mark?, colors: HarfColors, size: Dp = 46.dp, active: Boolean = false) {
    // Active cell: accent border with a slow pulse so the player sees where input lands.
    val borderColor: Color
    val borderWidth: Dp
    if (active) {
        val transition = rememberInfiniteTransition(label = "activeCell")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "activeAlpha",
        )
        borderColor = colors.accent.copy(alpha = alpha)
        borderWidth = 2.dp
    } else {
        borderColor = colors.rule
        borderWidth = 1.dp
    }
    Box(
        modifier = Modifier.size(size).border(borderWidth, borderColor, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) FeedbackMark(mark, colors, Modifier.fillMaxSize())
        if (grapheme != null) {
            // Text scales with the tile — exactly 20.sp at the standard 46.dp. Over a filled mark
            // the letter takes the style's on-mark color plus a contrasting halo, so it stays
            // legible on any fill (full or partial) in any edition, not just on the tile ground.
            val letterColor = if (mark != null) LocalMarkStyle.current.letterColor(mark, colors) else colors.ink
            Text(
                grapheme.uppercase(),
                color = letterColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * (20f / 46f)).sp,
                style = glyphHalo(mark, letterColor, colors, size.value * 0.18f),
            )
        }
    }
}

/** Feedback drawn by the active [Mark] Style (Scribble / Fill / Outline). */
@Composable
fun FeedbackMark(mark: Mark, colors: HarfColors, modifier: Modifier = Modifier) {
    LocalMarkStyle.current.Draw(mark, colors, modifier)
}

@Composable
fun KeyboardView(
    config: LanguageConfig,
    keyStates: Map<String, Mark>,
    onKey: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHarfColors.current
    val spacing = 4.dp
    val rows = config.keyboard
    val lastIdx = rows.lastIndex
    // System-keyboard layout: delete at the trailing end of the top row, enter at the trailing
    // end of the last row. Action keys are 1.5x a letter key. When appending them would shrink a
    // key below a tappable minimum (e.g. the 12-key Uzbek-Cyrillic rows on a narrow phone), fall
    // back to a separate action row so nothing clips.
    val actionW = 1.5f
    BoxWithConstraints(modifier) {
        // Smallest letter width any row demands once its inline action (if any) is included.
        val inlineKeyW = rows.indices.minOf { i ->
            val actions = (if (i == 0) 1 else 0) + (if (i == lastIdx) 1 else 0)
            val units = rows[i].size + actions * actionW
            val gaps = rows[i].size + actions - 1
            (maxWidth - spacing * gaps) / units
        }
        val inline = inlineKeyW >= 24.dp
        val keyW = if (inline) {
            inlineKeyW.coerceAtMost(56.dp)
        } else {
            val maxKeys = rows.maxOf { it.size }
            ((maxWidth - spacing * (maxKeys - 1)) / maxKeys).coerceAtMost(56.dp)
        }
        // Key height is set to a real on-screen keyboard height (~Gboard), independent of the
        // narrow per-key width that a 12-key Cyrillic row forces, so the keyboard doesn't sit
        // short under a large empty gap. Cyrillic keys are legitimately narrow — Gboard's are too.
        val keyH = 48.dp
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            rows.forEachIndexed { i, rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (k in rowKeys) KeyCap(k, keyStates[k], c, keyW, keyH) { onKey(k) }
                    if (inline && i == 0) ActionKey("⌫", c, keyW * actionW, keyH, onDelete)
                    if (inline && i == lastIdx) ActionKey("⏎", c, keyW * actionW, keyH, onEnter)
                }
            }
            if (!inline) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCap("ENTER", c, keyH, onEnter)
                    ActionCap("⌫", c, keyH, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ActionKey(label: String, c: HarfColors, width: Dp, height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(5.dp))
            .background(c.key)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun KeyCap(label: String, mark: Mark?, c: HarfColors, width: Dp, height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(5.dp))
            .background(c.key)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) FeedbackMark(mark, c, Modifier.fillMaxSize().padding(4.dp))
        val letterColor = if (mark != null) LocalMarkStyle.current.letterColor(mark, c) else c.ink
        Text(
            label.uppercase(),
            color = letterColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            style = glyphHalo(mark, letterColor, c, 6f),
        )
    }
}

@Composable
private fun ActionCap(label: String, c: HarfColors, height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(5.dp))
            .background(c.key)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ResultView(state: GameState, languageDisplay: String, puzzleNumber: Long, onPaywall: () -> Unit = {}) {
    val c = LocalHarfColors.current
    val settings = koinInject<AppSettings>()
    val sharer = koinInject<Sharer>()
    val purchases = koinInject<PurchaseController>()
    val paletteId by settings.paletteId.collectAsState()
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (state.status == GameStatus.Won) stringResource(Res.string.result_solved, state.submitted.size, state.maxAttempts) else stringResource(Res.string.result_out_of_tries),
            color = c.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        )
        if (state.status == GameStatus.Lost && state.revealed != null) {
            Text(state.revealed.joinToString("").uppercase(), color = c.present, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        val shareText = ShareGrid.build(
            paletteId = paletteId,
            languageDisplay = languageDisplay,
            puzzleNumber = puzzleNumber,
            rows = state.submitted.map { it.marks },
            won = state.status == GameStatus.Won,
            maxAttempts = state.maxAttempts,
            isArchive = state.roundKind == uz.abumme.harfgame.data.sync.RoundKind.ARCHIVE,
            hardMode = state.hardMode,
        )
        Button(onClick = { scope.launch { sharer.share(shareText) } }) { Text(stringResource(Res.string.action_share)) }
        OutlinedButton(onClick = { sharer.copy(shareText) }) { Text(stringResource(Res.string.action_copy)) }
        // Only when the store is configured (real RevenueCat key); blank key hides the support link.
        if (purchases.isAvailable) {
            TextButton(onClick = onPaywall) { Text(stringResource(Res.string.settings_support_harf), color = c.muted) }
        }
    }
}
