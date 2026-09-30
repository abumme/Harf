package uz.abumme.harfgame.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.feature.archive.ArchiveScreen
import uz.abumme.harfgame.feature.game.GameScreen
import uz.abumme.harfgame.feature.home.HomeScreen
import uz.abumme.harfgame.feature.paywall.PaywallScreen
import uz.abumme.harfgame.feature.purchases.ManagePurchasesScreen
import uz.abumme.harfgame.feature.settings.SettingsScreen
import uz.abumme.harfgame.feature.stats.StatsScreen

/** Type-safe routes. Feature graphs add destinations here as they land. */
@Serializable
object Home

@Serializable
object Archive

@Serializable
data class Game(val languageId: String)

@Serializable
object Stats

@Serializable
object Settings

@Serializable
object Paywall

@Serializable
object ManagePurchases

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onPlay = { languageId -> navController.navigate(Game(languageId)) },
                onArchive = { navController.navigate(Archive) },
                onStats = { navController.navigate(Stats) },
                onSettings = { navController.navigate(Settings) },
                onPaywall = { navController.navigate(Paywall) },
            )
        }
        composable<Archive> {
            ArchiveScreen(
                onBack = { navController.popBackStack() },
                onOpenPuzzle = { languageId, epochDay ->
                    // Navigation to historical puzzle
                    navController.navigate(Game(languageId))
                },
                onPaywall = { navController.navigate(Paywall) },
            )
        }
        composable<Game> { entry ->
            GameScreen(
                languageId = entry.toRoute<Game>().languageId,
                onBack = { navController.popBackStack() },
                onPaywall = { navController.navigate(Paywall) },
            )
        }
        composable<Stats> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPaywall = { navController.navigate(Paywall) },
                onCustomerCenter = { navController.navigate(ManagePurchases) },
            )
        }
        composable<Paywall> {
            PaywallScreen(onBack = { navController.popBackStack() })
        }
        composable<ManagePurchases> {
            ManagePurchasesScreen(onBack = { navController.popBackStack() })
        }
    }
}
