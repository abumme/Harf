package uz.abumme.harfgame.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import uz.abumme.harfgame.theme.LocalHarfColors

/** Type-safe routes. Feature graphs add destinations here as they land. */
@Serializable
object Home

@Serializable
object Archive

/** A game round: today's daily when [epochDay] is null, otherwise that archived day (Founder archive). */
@Serializable
data class Game(val languageId: String, val epochDay: Long? = null)

@Serializable
object Stats

@Serializable
object Settings

@Serializable
object Paywall

@Serializable
object ManagePurchases

/**
 * A destination drawn on its own opaque paper, outside the screen's inset padding, so a transition
 * never shows two pages (or the system-bar strips) through each other. The page is also a hit target:
 * a tap on its empty parts stops there instead of reaching the page sliding underneath it.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalHarfColors.current.paper)
                // Observes without consuming, so the page's own clicks and scrolls are unaffected.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) { scope.content(entry) }
    }
}

/**
 * Runs [block] only while this entry is the settled, current destination: NavHost resumes an entry
 * after its enter transition and stops resuming it as soon as it starts leaving.
 */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // The in-app back control never pops the start destination (that would leave an empty NavHost).
    val back: () -> Unit = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
    // All six are set: overriding only enter/exit would let a platform default derive the pop pair from them.
    NavHost(
        navController = navController,
        startDestination = Home,
        enterTransition = navEnter,
        exitTransition = navExit,
        popEnterTransition = navPopEnter,
        popExitTransition = navPopExit,
        predictivePopEnterTransition = navPredictivePopEnter,
        predictivePopExitTransition = navPredictivePopExit,
    ) {
        screen<Home> { entry ->
            HomeScreen(
                onPlay = { languageId -> entry.ifResumed { navController.navigate(Game(languageId)) } },
                onArchive = dropUnlessResumed(entry) { navController.navigate(Archive) },
                onStats = dropUnlessResumed(entry) { navController.navigate(Stats) },
                onSettings = dropUnlessResumed(entry) { navController.navigate(Settings) },
                onPaywall = dropUnlessResumed(entry) { navController.navigate(Paywall) },
            )
        }
        screen<Archive> { entry ->
            ArchiveScreen(
                onBack = dropUnlessResumed(entry, back),
                onOpenPuzzle = { languageId, epochDay ->
                    entry.ifResumed { navController.navigate(Game(languageId, epochDay)) }
                },
                onPaywall = dropUnlessResumed(entry) { navController.navigate(Paywall) },
            )
        }
        screen<Game> { entry ->
            val route = entry.toRoute<Game>()
            GameScreen(
                languageId = route.languageId,
                epochDay = route.epochDay,
                onBack = dropUnlessResumed(entry, back),
                onPaywall = dropUnlessResumed(entry) { navController.navigate(Paywall) },
            )
        }
        screen<Stats> { entry ->
            StatsScreen(onBack = dropUnlessResumed(entry, back))
        }
        screen<Settings> { entry ->
            SettingsScreen(
                onBack = dropUnlessResumed(entry, back),
                onPaywall = dropUnlessResumed(entry) { navController.navigate(Paywall) },
                onCustomerCenter = dropUnlessResumed(entry) { navController.navigate(ManagePurchases) },
            )
        }
        screen<Paywall> { entry ->
            PaywallScreen(onBack = dropUnlessResumed(entry, back))
        }
        screen<ManagePurchases> { entry ->
            ManagePurchasesScreen(onBack = dropUnlessResumed(entry, back))
        }
    }
}
