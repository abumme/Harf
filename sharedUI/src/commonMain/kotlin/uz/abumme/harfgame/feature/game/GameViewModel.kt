package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.core.mvi.BaseViewModel
import uz.abumme.harfgame.core.mvi.UiAction
import uz.abumme.harfgame.core.mvi.UiEvent
import uz.abumme.harfgame.core.mvi.UiState
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.ScoreResult
import uz.abumme.harfgame.engine.Scorer
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle

data class GameRow(val graphemes: List<String>, val marks: List<Mark>)

enum class GameStatus { Playing, Won, Lost }

data class GameState(
    val languageId: String,
    val tileCount: Int,
    val maxAttempts: Int = 6,
    val submitted: List<GameRow> = emptyList(),
    val current: List<String> = emptyList(),
    val status: GameStatus = GameStatus.Playing,
    val keyStates: Map<String, Mark> = emptyMap(),
    val revealed: List<String>? = null,
) : UiState

sealed interface GameAction : UiAction {
    data class Input(val grapheme: String) : GameAction
    data object Delete : GameAction
    data object Submit : GameAction
}

sealed interface GameEvent : UiEvent {
    data object Incomplete : GameEvent
    data object InvalidGuess : GameEvent
    data class RoundEnded(val won: Boolean) : GameEvent
}

/**
 * Owns one round of one language/script. Scoring and validation are delegated to the engine.
 * Optionally restores an in-progress round and reports a [ResultRecord] once on finish.
 * Uzbek script switching is handled by the screen (a new round for the other script).
 */
class GameViewModel(
    private val puzzle: DailyPuzzle,
    private val pack: WordPack,
    restore: InProgressRound? = null,
    private val onFinish: (ResultRecord) -> Unit = {},
) : BaseViewModel<GameState, GameAction, GameEvent>(initialState(puzzle, restore)) {

    private val answer = puzzle.answer

    override fun onAction(action: GameAction) {
        if (currentState.status != GameStatus.Playing) return
        when (action) {
            is GameAction.Input -> setState { if (current.size >= tileCount) this else copy(current = current + action.grapheme) }
            GameAction.Delete -> setState { if (current.isEmpty()) this else copy(current = current.dropLast(1)) }
            GameAction.Submit -> submit()
        }
    }

    /** Snapshot for persistence. */
    fun snapshot(): InProgressRound = InProgressRound(
        languageId = puzzle.languageId,
        puzzleDay = puzzle.epochDay,
        rows = currentState.submitted.map { r -> InProgressRow(r.graphemes, r.marks.map { it.ordinal }) },
        current = currentState.current,
    )

    private fun submit() {
        val s = currentState
        if (s.current.size < s.tileCount) {
            sendEvent(GameEvent.Incomplete)
            return
        }
        if (!pack.isValidGuess(s.current)) {
            sendEvent(GameEvent.InvalidGuess)
            return
        }
        val marks = (Scorer.score(s.current, answer) as ScoreResult.Scored).marks
        val submitted = s.submitted + GameRow(s.current, marks)
        val keyStates = mergeKeyStates(s.keyStates, s.current, marks)
        val won = marks.all { it == Mark.CORRECT }
        val lost = !won && submitted.size >= s.maxAttempts

        setState {
            copy(
                submitted = submitted,
                current = emptyList(),
                keyStates = keyStates,
                status = if (won) GameStatus.Won else if (lost) GameStatus.Lost else GameStatus.Playing,
                revealed = if (lost) answer else null,
            )
        }
        if (won || lost) {
            onFinish(ResultRecord(puzzle.languageId, puzzle.epochDay, won, submitted.size))
            sendEvent(GameEvent.RoundEnded(won))
        }
    }

    companion object {
        private fun initialState(puzzle: DailyPuzzle, restore: InProgressRound?): GameState {
            val base = GameState(languageId = puzzle.languageId, tileCount = puzzle.tileCount)
            if (restore == null || restore.languageId != puzzle.languageId || restore.puzzleDay != puzzle.epochDay) {
                return base
            }
            val rows = restore.rows.map { row -> GameRow(row.graphemes, row.marks.map { Mark.entries[it] }) }
            var keys = emptyMap<String, Mark>()
            for (row in rows) keys = mergeKeyStates(keys, row.graphemes, row.marks)
            // recompute terminal status from the restored rows so a finished round doesn't reopen
            val won = rows.lastOrNull()?.marks?.all { it == Mark.CORRECT } == true
            val lost = !won && rows.size >= base.maxAttempts
            val status = if (won) GameStatus.Won else if (lost) GameStatus.Lost else GameStatus.Playing
            return base.copy(
                submitted = rows,
                current = if (status == GameStatus.Playing) restore.current else emptyList(),
                keyStates = keys,
                status = status,
                revealed = if (lost) puzzle.answer else null,
            )
        }

        /** Best-known state per grapheme; never downgrades (correct > present > absent). */
        private fun mergeKeyStates(existing: Map<String, Mark>, graphemes: List<String>, marks: List<Mark>): Map<String, Mark> {
            val out = existing.toMutableMap()
            for (i in graphemes.indices) {
                val g = graphemes[i]
                val m = marks[i]
                val prev = out[g]
                if (prev == null || rank(m) > rank(prev)) out[g] = m
            }
            return out
        }

        private fun rank(m: Mark): Int = when (m) {
            Mark.CORRECT -> 3
            Mark.PRESENT -> 2
            Mark.ABSENT -> 1
        }
    }
}
