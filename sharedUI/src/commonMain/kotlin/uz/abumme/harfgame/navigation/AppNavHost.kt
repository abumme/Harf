package uz.abumme.harfgame.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.billing.HostedCustomerCenter
import uz.abumme.harfgame.feature.game.GameScreen
import uz.abumme.harfgame.feature.home.HomeScreen
import uz.abumme.harfgame.feature.paywall.PaywallScreen
import uz.abumme.harfgame.feature.settings.SettingsScreen
import uz.abumme.harfgame.feature.stats.StatsScreen

/** Type-safe routes. Feature graphs add destinations here as they land. */
@Serializable
object Home

@Serializable
data class Game(val languageId: String)

@Serializable
object Stats

@Serializable
object Settings

@Serializable
object Paywall

@Serializable
object CustomerCenter

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onPlay = { languageId -> navController.navigate(Game(languageId)) },
                onStats = { navController.navigate(Stats) },
                onSettings = { navController.navigate(Settings) },
            )
        }
        composable<Game> { entry ->
            GameScreen(
                languageId = entry.toRoute<Game>().languageId,
                onPaywall = { navController.navigate(Paywall) },
            )
        }
        composable<Stats> {
            StatsScreen()
        }
        composable<Settings> {
            SettingsScreen(
                onPaywall = { navController.navigate(Paywall) },
                onCustomerCenter = { navController.navigate(CustomerCenter) },
            )
        }
        composable<Paywall> {
            PaywallScreen(onBack = { navController.popBackStack() })
        }
        composable<CustomerCenter> {
            HostedCustomerCenter(onDismiss = { navController.popBackStack() })
        }
    }
}
