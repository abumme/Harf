package uz.abumme.harfgame.data.archive

import eu.anifantakis.lib.ksafe.KSafe
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.RoundHolder

/**
 * Persists in-progress archive rounds keyed by owner, language, day, and run id.
 * Completely isolated from official daily rounds in RoundStore.
 */
class ArchiveRoundStore(private val ksafe: KSafe) {

    suspend fun save(ownerId: String, runId: String, round: InProgressRound) {
        ksafe.put(key(ownerId, round.languageId, round.puzzleDay, runId), RoundHolder(round))
    }

    suspend fun load(ownerId: String, languageId: String, puzzleDay: Long, runId: String): InProgressRound? {
        val stored = ksafe.get(key(ownerId, languageId, puzzleDay, runId), RoundHolder()).round ?: return null
        return stored.takeIf { it.languageId == languageId && it.puzzleDay == puzzleDay }
    }

    suspend fun clear(ownerId: String, languageId: String, puzzleDay: Long, runId: String) {
        ksafe.put(key(ownerId, languageId, puzzleDay, runId), RoundHolder(null))
    }

    private fun key(ownerId: String, languageId: String, day: Long, runId: String) =
        "$KEY.$ownerId.$languageId.$day.$runId"

    companion object {
        private const val KEY = "archive.inProgressRound"
    }
}
