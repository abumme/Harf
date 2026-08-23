package uz.abumme.harfgame.data.stats

import eu.anifantakis.lib.ksafe.KSafe

/**
 * Persists the single in-progress round so a killed app resumes today's board. A stored round
 * from a different day (or language) is treated as absent — a new day starts fresh.
 */
class RoundStore(private val ksafe: KSafe) {

    suspend fun save(round: InProgressRound) = ksafe.put(KEY, RoundHolder(round))

    suspend fun load(languageId: String, puzzleDay: Long): InProgressRound? {
        val stored = ksafe.get(KEY, RoundHolder()).round ?: return null
        return stored.takeIf { it.languageId == languageId && it.puzzleDay == puzzleDay }
    }

    suspend fun clear() = ksafe.put(KEY, RoundHolder(null))

    companion object {
        private const val KEY = "stats.inProgressRound"
    }
}
