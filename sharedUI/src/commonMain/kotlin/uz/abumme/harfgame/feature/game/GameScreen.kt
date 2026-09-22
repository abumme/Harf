package uz.abumme.harfgame.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import harf_game.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uz.abumme.harfgame.feature.onboarding.MarkLegend
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.feature.result.ShareGrid
import uz.abumme.harfgame.feature.share.Sharer
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfColors
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.LocalMarkStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope

private data class Loaded(
    val puzzle: DailyPuzzle,
    val config: LanguageConfig,
    val pack: WordPack,
    val restore: InProgressRound?,
)

@Composable
fun GameScreen(languageId: String, onPaywall: () -> Unit = {}) {
    val provider = koinInject<DailyPuzzleProvider>()
    val packs = koinInject<WordPackRepository>()
    val registry = koinInject<LanguageRegistry>()
    val resultLog = koinInject<ResultLog>()
    val roundStore = koinInject<RoundStore>()
    val syncManager = koinInject<uz.abumme.harfgame.data.stats.SyncManager>()
    val gamesServices = koinInject<uz.abumme.harfgame.games.GamesServices>()
    val scope = rememberCoroutineScope()

    // active script (Uzbek can switch latn <-> cyrl for the same daily lexeme)
    var script by remember { mutableStateOf(languageId) }
    var loaded by remember { mutableStateOf<Loaded?>(null) }

    LaunchedEffect(script) {
        loaded = null
        val puzzle = provider.daily(script)
        val pack = packs.load(script)
        val config = registry.config(script)!!
        val restore = roundStore.load(script, puzzle.epochDay)
        loaded = Loaded(puzzle, config, pack, restore)
    }

    val data = loaded ?: return
    val (puzzle, config, pack, restore) = data

    val vm = viewModel(key = script) {
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
        if (message != null) { kotlinx.coroutines.delay(1500); message = null }
    }
    LaunchedEffect(hardViolation) {
        if (hardViolation != null) { kotlinx.coroutines.delay(1500); hardViolation = null }
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
            // Hard Mode toggle (pre-round only, gated by Founder entitlement)
            if (state.submitted.isEmpty() && state.status == GameStatus.Playing) {
                val isFounder = uz.abumme.harfgame.billing.EntitlementGate.lifetimeExtrasUnlocked(
                    koinInject<uz.abumme.harfgame.billing.EntitlementRepository>().entitlements.collectAsState().value
                )
                if (isFounder) {
                    TextButton(onClick = { vm.onAction(GameAction.ToggleHardMode) }) {
                        Text(
                            if (state.hardMode) "* " + stringResource(Res.string.game_hard_mode) else stringResource(Res.string.game_hard_mode),
                            color = if (state.hardMode) colors.accent else colors.muted,
                            fontWeight = if (state.hardMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    TextButton(onClick = onPaywall) {
                        Text(
                            stringResource(Res.string.game_hard_mode) + " 🔒",
                            color = colors.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else if (state.hardMode) {
                Text(
                    "* " + stringResource(Res.string.game_hard_mode),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            val helpLabel = stringResource(Res.string.help)
            TextButton(onClick = { showHelp = true }, modifier = Modifier.semantics { contentDescription = helpLabel }) {
                Text("?", color = colors.muted)
            }
        }
    }
    val switchScript: (String) -> Unit = { targetScript ->
        if (targetScript != script) {
            val currentSnapshot = vm.snapshot()
            scope.launch {
                roundStore.save(currentSnapshot)
                val targetPuzzle = provider.daily(targetScript)
                val converted = uz.abumme.harfgame.lang.UzbekScriptConverter.convertRound(
                    round = currentSnapshot,
                    targetScript = targetScript,
                    targetAnswer = targetPuzzle.answer,
                )
                roundStore.save(converted)
                script = targetScript
            }
        }
    }
    val chips: @Composable () -> Unit = {
        if (languageId.startsWith("uz")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScriptChip("Lotin", script == "uz-latn") { switchScript("uz-latn") }
                ScriptChip("Кирилл", script == "uz-cyrl") { switchScript("uz-cyrl") }
            }
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
                onKey = { vm.onAction(GameAction.Input(it)) },
                onDelete = { vm.onAction(GameAction.Delete) },
                onEnter = { vm.onAction(GameAction.Submit) },
            )
        } else {
            ResultView(state, config.displayName, puzzle.epochDay, onPaywall)
        }
    }
    // Board sized to fully fit whatever space it is given; capped at 46.dp so tall phones look unchanged.
    val board: @Composable (maxW: Dp, maxH: Dp) -> Unit = { maxW, maxH ->
        val gap = 6.dp
        val ts = minOf(
            46.dp,
            (maxW - gap * (state.tileCount - 1)) / state.tileCount,
            (maxH - gap * (state.maxAttempts - 1)) / state.maxAttempts,
        )
        BoardView(state, tileSize = ts)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(colors.paper)
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || state.status != GameStatus.Playing) {
                    return@onPreviewKeyEvent false
                }
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter -> { vm.onAction(GameAction.Submit); true }
                    Key.Backspace -> { vm.onAction(GameAction.Delete); true }
                    else -> {
                        val key = ev.utf16CodePoint.takeIf { it != 0 }
                            ?.let { keyLookup[it.toChar().toString().lowercase()] }
                        if (key != null) { vm.onAction(GameAction.Input(key)); true } else false
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

@Composable
private fun ScriptChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHarfColors.current
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) c.accent else c.card, RoundedCornerShape(20.dp))
            .border(1.dp, c.rule, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(label, color = if (selected) c.paper else c.muted, fontSize = 13.sp) }
}

@Composable
fun BoardView(state: GameState, modifier: Modifier = Modifier, tileSize: Dp = 46.dp) {
    val colors = LocalHarfColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in 0 until state.maxAttempts) {
            val row = state.submitted.getOrNull(r)
            val isCurrent = r == state.submitted.size && state.status == GameStatus.Playing
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until state.tileCount) {
                    val g = row?.graphemes?.getOrNull(c) ?: if (isCurrent) state.current.getOrNull(c) else null
                    Tile(g, row?.marks?.getOrNull(c), colors, tileSize)
                }
            }
        }
    }
}

@Composable
private fun Tile(grapheme: String?, mark: Mark?, colors: HarfColors, size: Dp = 46.dp) {
    Box(
        modifier = Modifier.size(size).border(1.dp, colors.rule, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) FeedbackMark(mark, colors, Modifier.fillMaxSize())
        if (grapheme != null) {
            // Text scales with the tile — exactly 20.sp at the standard 46.dp.
            Text(grapheme.uppercase(), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = (size.value * (20f / 46f)).sp)
        }
    }
}

/** Feedback drawn by the active [MarkStyle] (Scribble / Fill / Outline). */
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
    // Uniform key width sized to the widest row so even the 12-key Cyrillic layout fits any phone
    // (fixed-width keys used to clip the last column). Capped so a short row's keys don't balloon.
    val maxKeys = config.keyboard.maxOf { it.size }
    BoxWithConstraints(modifier) {
        val keyW = ((maxWidth - spacing * (maxKeys - 1)) / maxKeys).coerceAtMost(44.dp)
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (rowKeys in config.keyboard) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (k in rowKeys) KeyCap(k, keyStates[k], c, keyW) { onKey(k) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionCap("ENTER", c, onEnter)
                ActionCap("⌫", c, onDelete)
            }
        }
    }
}

@Composable
private fun KeyCap(label: String, mark: Mark?, c: HarfColors, width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = 42.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(c.key)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) FeedbackMark(mark, c, Modifier.fillMaxSize().padding(4.dp))
        Text(label.uppercase(), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionCap(label: String, c: HarfColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(42.dp)
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
