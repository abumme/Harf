package uz.abumme.harfgame.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.statistics
import harf_game.sharedui.generated.resources.stats_best
import harf_game.sharedui.generated.resources.stats_played
import harf_game.sharedui.generated.resources.stats_streak
import harf_game.sharedui.generated.resources.stats_win_rate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.core.mvi.BaseViewModel
import uz.abumme.harfgame.core.mvi.UiAction
import uz.abumme.harfgame.core.mvi.UiEvent
import uz.abumme.harfgame.core.mvi.UiState
import uz.abumme.harfgame.data.stats.PlayerStats
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.StreakStats
import uz.abumme.harfgame.data.stats.Streaks
import uz.abumme.harfgame.data.stats.toDto
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class StatsEntry(
    val languageId: String,
    val display: String,
    val stats: PlayerStats,
    val streak: StreakStats,
)

data class StatsState(val entries: List<StatsEntry> = emptyList()) : UiState
object StatsNoAction : UiAction
object StatsNoEvent : UiEvent

@OptIn(ExperimentalTime::class)
class StatsViewModel(
    resultLog: ResultLog,
    registry: LanguageRegistry,
    private val puzzles: DailyPuzzleProvider,
) : BaseViewModel<StatsState, StatsNoAction, StatsNoEvent>(StatsState()) {
    init {
        viewModelScope.launch {
            // The shared rules (also the staff panel's player detail) read the synced record shape.
            val records = resultLog.all().map { it.toDto() }
            val now = Clock.System.now()
            setState {
                copy(entries = registry.ids.sorted().map { id ->
                    StatsEntry(
                        languageId = id,
                        display = registry.config(id)!!.displayName,
                        stats = Streaks.stats(records, id),
                        streak = Streaks.streak(records, id, puzzles.epochDay(id, now)),
                    )
                })
            }
        }
    }

    override fun onAction(action: StatsNoAction) = Unit
}

@Composable
fun StatsScreen() {
    val resultLog = koinInject<ResultLog>()
    val registry = koinInject<LanguageRegistry>()
    val puzzles = koinInject<DailyPuzzleProvider>()
    val vm = viewModel { StatsViewModel(resultLog, registry, puzzles) }
    val state by vm.state.collectAsState()
    StatsContent(state)
}

/** Stateless content — previewable with sample entries. */
@Composable
fun StatsContent(state: StatsState) {
    val c = LocalHarfColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.statistics), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        for (e in state.entries) {
            HorizontalDivider(color = c.rule)
            Text(e.display, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat(stringResource(Res.string.stats_played), e.stats.played.toString(), c.ink, c.muted)
                Stat(stringResource(Res.string.stats_win_rate), (e.stats.winRate * 100).toInt().toString(), c.ink, c.muted)
                Stat(stringResource(Res.string.stats_streak), "🔥 ${e.streak.current}", c.accent, c.muted)
                Stat(stringResource(Res.string.stats_best), e.streak.best.toString(), c.ink, c.muted)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, labelColor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = labelColor, fontSize = 11.sp)
    }
}
