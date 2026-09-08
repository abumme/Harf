package uz.abumme.harfgame.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_got_it
import harf_game.sharedui.generated.resources.help
import harf_game.sharedui.generated.resources.howto_body
import harf_game.sharedui.generated.resources.howto_title
import harf_game.sharedui.generated.resources.not_enough_letters
import harf_game.sharedui.generated.resources.not_in_word_list
import harf_game.sharedui.generated.resources.action_copy
import harf_game.sharedui.generated.resources.action_share
import harf_game.sharedui.generated.resources.result_out_of_tries
import harf_game.sharedui.generated.resources.result_solved
import harf_game.sharedui.generated.resources.settings_support_harf
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
import androidx.compose.ui.Alignment
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

    val data = loaded
    if (data == null) return
    val (puzzle, config, pack, restore) = data

    val vm = viewModel(key = script) {
        GameViewModel(puzzle, pack, restore) { record ->
            scope.launch {
                resultLog.record(record)
                // B11: keep the finished round persisted so reopening today shows the result,
                // not a blank editable board. The snapshot is saved by the effect below.
                syncManager.pushStats()
            }
        }
    }
    val state by vm.state.collectAsState()
    val colors = LocalHarfColors.current
    var showHelp by remember { mutableStateOf(false) }

    // transient feedback for rejected submissions (too short / not in dictionary)
    var message by remember { mutableStateOf<String?>(null) }
    val incompleteMsg = stringResource(Res.string.not_enough_letters)
    val invalidMsg = stringResource(Res.string.not_in_word_list)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                GameEvent.Incomplete -> message = incompleteMsg
                GameEvent.InvalidGuess -> message = invalidMsg
                is GameEvent.RoundEnded -> {}
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(1500); message = null }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val helpLabel = stringResource(Res.string.help)
            TextButton(onClick = { showHelp = true }, modifier = Modifier.semantics { contentDescription = helpLabel }) {
                Text("?", color = colors.muted)
            }
        }
        if (languageId.startsWith("uz")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScriptChip("Lotin", script == "uz-latn") { script = "uz-latn" }
                ScriptChip("Кирилл", script == "uz-cyrl") { script = "uz-cyrl" }
            }
        }
        BoardView(state)
        MarkLegend(Modifier.padding(vertical = 2.dp), compact = true)
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            message?.let { Text(it, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
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
fun BoardView(state: GameState, modifier: Modifier = Modifier) {
    val colors = LocalHarfColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in 0 until state.maxAttempts) {
            val row = state.submitted.getOrNull(r)
            val isCurrent = r == state.submitted.size && state.status == GameStatus.Playing
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until state.tileCount) {
                    val g = row?.graphemes?.getOrNull(c) ?: if (isCurrent) state.current.getOrNull(c) else null
                    Tile(g, row?.marks?.getOrNull(c), colors)
                }
            }
        }
    }
}

@Composable
private fun Tile(grapheme: String?, mark: Mark?, colors: HarfColors) {
    Box(
        modifier = Modifier.size(46.dp).border(1.dp, colors.rule, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) FeedbackMark(mark, colors, Modifier.fillMaxSize())
        if (grapheme != null) {
            Text(grapheme.uppercase(), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
        )
        Button(onClick = { scope.launch { sharer.share(shareText) } }) { Text(stringResource(Res.string.action_share)) }
        OutlinedButton(onClick = { sharer.copy(shareText) }) { Text(stringResource(Res.string.action_copy)) }
        // Only when the store is configured (real RevenueCat key); blank key hides the support link.
        if (purchases.isAvailable) {
            TextButton(onClick = onPaywall) { Text(stringResource(Res.string.settings_support_harf), color = c.muted) }
        }
    }
}
