package uz.abumme.harfgame.data.stats

import eu.anifantakis.lib.ksafe.KSafe

/**
 * Persists the in-progress round per language so a killed app resumes each board — and switching
 * Uzbek scripts (uz-latn <-> uz-cyrl) never clobbers the other script's progress. A stored round
 * from a different day is treated as absent — a new day starts fresh.
 */
class RoundStore(private val ksafe: KSafe) {

    suspend fun save(round: InProgressRound) = ksafe.put(key(round.languageId), RoundHolder(round))

    suspend fun load(languageId: String, puzzleDay: Long): InProgressRound? {
        val stored = ksafe.get(key(languageId), RoundHolder()).round ?: return null
        return stored.takeIf { it.languageId == languageId && it.puzzleDay == puzzleDay }
    }

    suspend fun clear(languageId: String) = ksafe.put(key(languageId), RoundHolder(null))

    private fun key(languageId: String) = "$KEY.$languageId"

    companion object {
        private const val KEY = "stats.inProgressRound"
    }
}
