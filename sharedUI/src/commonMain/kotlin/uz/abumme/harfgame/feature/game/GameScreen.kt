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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_copy
import harf_game.sharedui.generated.resources.action_back
import harf_game.sharedui.generated.resources.action_got_it
import harf_game.sharedui.generated.resources.action_share
import harf_game.sharedui.generated.resources.game_hard_mode_short
import harf_game.sharedui.generated.resources.help
import harf_game.sharedui.generated.resources.howto_body
import harf_game.sharedui.generated.resources.howto_title
import harf_game.sharedui.generated.resources.result_out_of_tries
import harf_game.sharedui.generated.resources.result_solved
import harf_game.sharedui.generated.resources.settings_support_harf
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
import uz.abumme.harfgame.theme.HarfColors
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfPaletteId
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

    // Feedback lives in a fixed-height status strip (see GameContent), so it never moves the board or
    // the keyboard. StripReducer holds the rules; this screen only runs the timer and the suggest call.
    var strip by remember { mutableStateOf<StripMessage?>(null) }
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            strip = StripReducer.onEvent(ev, vm.state.value.current.joinToString(""))
        }
    }
    LaunchedEffect(strip) {
        if (strip?.transient == true) {
            delay(1500.milliseconds)
            strip = StripReducer.onExpired(strip)
        }
    }
    // A new/edited guess withdraws the pending suggestion offer.
    LaunchedEffect(state.current) {
        strip = StripReducer.onGuessChanged(strip, state.current.joinToString(""))
    }
    val onSuggest: () -> Unit = {
        val offer = strip as? StripMessage.UnknownWord
        if (offer != null && !offer.sending) {
            strip = StripReducer.onSuggestStarted(strip)
            scope.launch {
                val result = syncManager.suggestWord(script, offer.word)
                strip = StripReducer.onSuggestResult(result is uz.abumme.harfgame.data.api.ApiResult.Success)
            }
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
    val keyLookup = remember(config) { (config.keyboard.flatten() + config.actionRowKeys).associateBy { it.lowercase() } }
    LaunchedEffect(script) { runCatching { focusRequester.requestFocus() } }

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
    // Uzbek switches script mid-round; the switch lives in the top bar so it costs no board height.
    val scriptSwitch: (@Composable () -> Unit)? = if (languageId.startsWith("uz")) {
        {
            uz.abumme.harfgame.theme.SegmentedSwitch(
                options = listOf("uz-latn" to "Lotin", "uz-cyrl" to "Кирилл"),
                selectedKey = selectedScript ?: script,
                onSelect = { switchScript(it) },
            )
        }
    } else {
        null
    }

    GameContent(
        state = state,
        config = config,
        strip = strip,
        onKey = { act(GameAction.Input(it)) },
        onDelete = { act(GameAction.Delete) },
        onEnter = { act(GameAction.Submit) },
        onSuggest = onSuggest,
        leading = {
            BackControl(onBack)
            HardModeControl(state, onToggle = { act(GameAction.ToggleHardMode) }, onPaywall = onPaywall)
        },
        center = scriptSwitch,
        trailing = { HelpControl(onClick = { showHelp = true }) },
        result = { ResultView(state, config.displayName, puzzle.epochDay, onPaywall) },
        modifier = Modifier
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
    )
}

/**
 * Hard Mode in the top bar: a toggle before the first guess (Founder entitlement; a locked chip that opens
 * the paywall otherwise), a plain label once the round is under way in hard mode, nothing otherwise.
 */
@Composable
private fun HardModeControl(state: GameState, onToggle: () -> Unit, onPaywall: () -> Unit) {
    val colors = LocalHarfColors.current
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
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    stringResource(Res.string.game_hard_mode_short),
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
                    stringResource(Res.string.game_hard_mode_short) + " 🔒",
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        }
    } else if (state.hardMode) {
        Text(
            stringResource(Res.string.game_hard_mode_short),
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The top bar's back control; the skeleton draws the same one, so it does not move when the round arrives. */
@Composable
private fun BackControl(onBack: () -> Unit) {
    val backLabel = stringResource(Res.string.action_back)
    TextButton(
        onClick = onBack,
        modifier = Modifier.size(40.dp).semantics { contentDescription = backLabel },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("‹", color = LocalHarfColors.current.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HelpControl(onClick: () -> Unit) {
    val helpLabel = stringResource(Res.string.help)
    TextButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).semantics { contentDescription = helpLabel },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("?", color = LocalHarfColors.current.muted)
    }
}

/**
 * The game page before its round is ready: the same insets, paddings and top bar as GameContent (back works;
 * help waits for the round), so the transition moves a real page and the top bar stays put when the board appears.
 */
@Composable
private fun GameSkeleton(onBack: () -> Unit) {
    val topBar: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(GameLayout.TOP_BAR.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackControl(onBack)
            HelpControl(onClick = {})
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(LocalHarfColors.current.paper)
            .padding(vertical = GameLayout.PAD_V.dp),
    ) {
        if (maxWidth > maxHeight) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = GameLayout.WIDE_PAD_H.dp),
                horizontalArrangement = Arrangement.spacedBy(GameLayout.WIDE_GAP.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) { topBar() }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) { topBar() }
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
    keyHeight: Dp = GameLayout.KEY_HEIGHTS.first().dp,
) {
    val c = LocalHarfColors.current
    val shape = remember(config) { KeyboardShape.of(config) }
    // The structure is fixed per language (letter rows + one action row); only the key size follows the
    // width. Measured against the full slot width so the sizes match GameLayout's plan for the viewport.
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val metrics = GameLayout.keyboard(maxWidth.value, shape, keyHeight.value)
        val keyW = metrics.keyWidth.dp
        val actionW = metrics.actionKeyWidth.dp
        val label = GameLayout.keyLabelSp(metrics.keyWidth).sp
        // Wrap the widest letter row rather than a fixed dp width: per-key pixel rounding would
        // otherwise clip the last key of a 12-key row on fractional densities.
        Column(
            Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(GameLayout.ROW_GAP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in shape.letterRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(GameLayout.KEY_GAP.dp)) {
                    for (k in row) KeyCap(k, keyStates[k], c, keyW, keyHeight, label) { onKey(k) }
                }
            }
            // ENTER leads and ⌫ trails, at the keyboard's edges; a language's action-row graphemes
            // (Uzbek Latin oʻ gʻ) sit between them like the symbols beside a system space bar.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionKey("ENTER", c, actionW, keyHeight, 12.sp, onEnter)
                Row(horizontalArrangement = Arrangement.spacedBy(GameLayout.KEY_GAP.dp)) {
                    for (k in shape.actionRowKeys) KeyCap(k, keyStates[k], c, keyW, keyHeight, label) { onKey(k) }
                }
                ActionKey("⌫", c, actionW, keyHeight, 16.sp, onDelete)
            }
        }
    }
}

@Composable
private fun ActionKey(label: String, c: HarfColors, width: Dp, height: Dp, fontSize: TextUnit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(5.dp))
            .background(c.key)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.ink, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
private fun KeyCap(label: String, mark: Mark?, c: HarfColors, width: Dp, height: Dp, fontSize: TextUnit, onClick: () -> Unit) {
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
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = glyphHalo(mark, letterColor, c, 6f),
        )
    }
}

@Composable
private fun ResultView(state: GameState, languageDisplay: String, puzzleNumber: Long, onPaywall: () -> Unit = {}) {
    val c = LocalHarfColors.current
    val sharer = koinInject<Sharer>()
    val purchases = koinInject<PurchaseController>()
    // The palette on screen, so the shared colours match the board (an unowned choice draws as the free one).
    val paletteId = LocalHarfPaletteId.current
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
