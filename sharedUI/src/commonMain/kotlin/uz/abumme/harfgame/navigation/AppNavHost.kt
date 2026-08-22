package uz.abumme.harfgame.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.feature.home.HomeScreen

/** Type-safe routes. Feature graphs add destinations here as they land. */
@Serializable
object Home

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> { HomeScreen() }
    }
}
