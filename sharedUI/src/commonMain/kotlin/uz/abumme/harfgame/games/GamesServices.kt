package uz.abumme.harfgame.games

import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.Streaks

/**
 * Aggregate player progress that maps to Play Games leaderboards/achievements. Computed from the
 * result log in shared code so it is platform-agnostic and testable; the platform layer only submits.
 */
data class GamesProgress(
    /** Best daily-solve streak across all languages (monotonic — only ever rises). */
    val bestStreak: Int,
    /** Total solved dailies across all languages. */
    val totalWins: Int,
    /** Any daily ever solved on the first guess. */
    val holeInOne: Boolean,
) {
    val hasFirstWin: Boolean get() = totalWins >= 1

    companion object {
        fun from(records: List<ResultRecord>, languages: Collection<String>): GamesProgress {
            val bestStreak = languages.maxOfOrNull { Streaks.streak(records, it, today = 0L).best } ?: 0
            val wins = records.filter { it.won }
            return GamesProgress(
                bestStreak = bestStreak,
                totalWins = wins.size,
                holeInOne = wins.any { it.attempts == 1 },
            )
        }
    }
}

/**
 * Play Games Services facade. Android provides a real implementation; every other platform binds a
 * no-op. All methods are best-effort and MUST NOT affect gameplay, the app account, or stats-sync.
 */
interface GamesServices {
    /** True only where Play Games is actually wired (Android with a configured app id). */
    val isAvailable: Boolean

    /** Submit leaderboards and unlock any earned achievements from [progress]. Silent when signed out. */
    fun submitProgress(progress: GamesProgress)

    /** Open the native Play Games leaderboards UI (no-op when unavailable). */
    fun showLeaderboards()

    /** Open the native Play Games achievements UI (no-op when unavailable). */
    fun showAchievements()
}

/** Default used on every non-Android platform (and Android without configuration). */
class NoOpGamesServices : GamesServices {
    override val isAvailable: Boolean = false
    override fun submitProgress(progress: GamesProgress) {}
    override fun showLeaderboards() {}
    override fun showAchievements() {}
}
