package uz.abumme.harfgame.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.feature.game.GameScreen
import uz.abumme.harfgame.feature.home.HomeScreen
import uz.abumme.harfgame.feature.stats.StatsScreen

/** Type-safe routes. Feature graphs add destinations here as they land. */
@Serializable
object Home

@Serializable
data class Game(val languageId: String)

@Serializable
object Stats

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onPlay = { languageId -> navController.navigate(Game(languageId)) },
                onStats = { navController.navigate(Stats) },
            )
        }
        composable<Game> { entry ->
            GameScreen(entry.toRoute<Game>().languageId)
        }
        composable<Stats> {
            StatsScreen()
        }
    }
}
