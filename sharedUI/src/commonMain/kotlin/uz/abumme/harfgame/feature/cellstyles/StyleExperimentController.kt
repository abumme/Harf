package uz.abumme.harfgame.feature.cellstyles

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import kotlin.time.ExperimentalTime

enum class ExperimentPhase { Rotating, PromptPending, Decided }

/**
 * Drives which mark style is active. For a new player the style rotates one-per-day across the
 * three styles over the first three distinct calendar days; after that a one-time prompt locks a
 * choice. A manual settings pick overrides and ends the experiment. State persists in KSafe;
 * "a day" is the local calendar date (advances once per new date, not per relaunch).
 */
@OptIn(ExperimentalTime::class)
class StyleExperimentController(
    private val ksafe: KSafe,
    private val log: StyleChoiceLog,
    private val today: () -> Long = { defaultToday() },
) {
    private val styles = HarfMarkStyleId.entries

    private val _activeStyle = MutableStateFlow(HarfMarkStyleId.Scribble)
    val activeStyle: StateFlow<HarfMarkStyleId> = _activeStyle.asStateFlow()

    private val _phase = MutableStateFlow(ExperimentPhase.Rotating)
    val phase: StateFlow<ExperimentPhase> = _phase.asStateFlow()

    init {
        refresh()
    }

    /** Call on each app open. Advances the day counter when the local date changed. */
    fun onAppOpen() {
        val t = today()
        if (t != ksafe.getDirect(KEY_LAST_DAY, Long.MIN_VALUE)) {
            ksafe.putDirect(KEY_DAY_COUNT, ksafe.getDirect(KEY_DAY_COUNT, 0) + 1)
            ksafe.putDirect(KEY_LAST_DAY, t)
        }
        if (phaseInt() == PHASE_ROTATING && ksafe.getDirect(KEY_DAY_COUNT, 0) > styles.size) {
            setPhaseInt(PHASE_PROMPT)
        }
        refresh()
    }

    suspend fun choose(id: HarfMarkStyleId) = decide(id, StyleChoiceSource.Experiment)

    /** Dismissing the prompt without choosing keeps a defined default. */
    suspend fun dismissPrompt() = decide(HarfMarkStyleId.Scribble, StyleChoiceSource.Experiment)

    suspend fun setFromSettings(id: HarfMarkStyleId) = decide(id, StyleChoiceSource.Settings)

    private suspend fun decide(id: HarfMarkStyleId, source: StyleChoiceSource) {
        ksafe.putDirect(KEY_CHOSEN, id.ordinal)
        setPhaseInt(PHASE_DECIDED)
        log.record(id.name, source, today())
        refresh()
    }

    private fun refresh() {
        _activeStyle.value = computeActive()
        _phase.value = when (phaseInt()) {
            PHASE_PROMPT -> ExperimentPhase.PromptPending
            PHASE_DECIDED -> ExperimentPhase.Decided
            else -> ExperimentPhase.Rotating
        }
    }

    private fun computeActive(): HarfMarkStyleId = when (phaseInt()) {
        PHASE_DECIDED -> styles.getOrElse(ksafe.getDirect(KEY_CHOSEN, 0)) { HarfMarkStyleId.Scribble }
        else -> {
            val idx = (ksafe.getDirect(KEY_DAY_COUNT, 0) - 1).coerceIn(0, styles.lastIndex)
            styles[idx]
        }
    }

    private fun phaseInt() = ksafe.getDirect(KEY_PHASE, PHASE_ROTATING)
    private fun setPhaseInt(p: Int) = ksafe.putDirect(KEY_PHASE, p)

    companion object {
        private const val KEY_DAY_COUNT = "cellStyles.dayCount"
        private const val KEY_LAST_DAY = "cellStyles.lastDayCounted"
        private const val KEY_PHASE = "cellStyles.phase"
        private const val KEY_CHOSEN = "cellStyles.chosen"
        private const val PHASE_ROTATING = 0
        private const val PHASE_PROMPT = 1
        private const val PHASE_DECIDED = 2

        private fun defaultToday(): Long =
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
    }
}
