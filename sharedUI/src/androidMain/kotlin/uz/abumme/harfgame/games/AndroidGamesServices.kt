package uz.abumme.harfgame.games

import android.app.Activity
import android.content.Context
import com.google.android.gms.games.PlayGames
import uz.abumme.harfgame.R
import uz.abumme.harfgame.data.auth.CurrentActivityProvider

/**
 * Real Play Games Services on Android. Best-effort by design: every action needs a current Activity
 * and a signed-in player; when either is missing it silently no-ops so gameplay is never affected.
 * Fully isolated — reads only [GamesProgress] and Play Games clients, never the app account or sync.
 */
class AndroidGamesServices(private val context: Context) : GamesServices {

    private fun id(resId: Int): String = context.getString(resId).trim()

    override val isAvailable: Boolean = id(R.string.app_id).isNotEmpty()

    override fun submitProgress(progress: GamesProgress) {
        if (!isAvailable) return
        val activity = CurrentActivityProvider.current() ?: return
        // Only act for an authenticated player; a signed-out player just skips (no prompt here).
        runCatching {
            PlayGames.getGamesSignInClient(activity).isAuthenticated
                .addOnSuccessListener { result ->
                    if (result.isAuthenticated) submit(activity, progress)
                }
        }
    }

    private fun submit(activity: Activity, p: GamesProgress) {
        runCatching {
            val leaderboards = PlayGames.getLeaderboardsClient(activity)
            leaderboards.submitScore(id(R.string.leaderboard_streak_masters), p.bestStreak.toLong())
            leaderboards.submitScore(id(R.string.leaderboard_puzzles_solved), p.totalWins.toLong())

            val achievements = PlayGames.getAchievementsClient(activity)
            if (p.hasFirstWin) achievements.unlock(id(R.string.achievement_first_win))
            if (p.bestStreak >= 7) achievements.unlock(id(R.string.achievement_week_streak))
            if (p.bestStreak >= 30) achievements.unlock(id(R.string.achievement_month_streak))
            if (p.holeInOne) achievements.unlock(id(R.string.achievement_hole_in_one))
            // Centurion is incremental (100 steps): set absolute progress, capped.
            achievements.setSteps(id(R.string.achievement_centurion), p.totalWins.coerceAtMost(100))
        }
    }

    override fun showLeaderboards() = openIntent { activity ->
        PlayGames.getLeaderboardsClient(activity).allLeaderboardsIntent
    }

    override fun showAchievements() = openIntent { activity ->
        PlayGames.getAchievementsClient(activity).achievementsIntent
    }

    /** Ensure sign-in, then launch the requested native PGS screen. Best-effort. */
    private fun openIntent(intentTask: (Activity) -> com.google.android.gms.tasks.Task<android.content.Intent>) {
        if (!isAvailable) return
        val activity = CurrentActivityProvider.current() ?: return
        runCatching {
            val signIn = PlayGames.getGamesSignInClient(activity)
            signIn.isAuthenticated.addOnSuccessListener { result ->
                fun launch() = intentTask(activity).addOnSuccessListener { intent ->
                    runCatching { activity.startActivityForResult(intent, RC_GAMES_UI) }
                }
                if (result.isAuthenticated) launch()
                else signIn.signIn().addOnSuccessListener { launch() }
            }
        }
    }

    private companion object {
        const val RC_GAMES_UI = 9001
    }
}
