package uz.abumme.harfgame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.feature.cellstyles.MarkStyleHost
import uz.abumme.harfgame.feature.onboarding.OnboardingIntro
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.navigation.AppNavHost
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.isDark

/**
 * App root: applies the Harf theme (the chosen palette, if the account may use it, on the ground the
 * saved theme mode resolves to) and hosts the navigation graph. [onThemeChanged] lets platform
 * wrappers sync system-bar style to the resolved ground.
 */
@Composable
fun App(onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}) {
    val settings = koinInject<AppSettings>()
    val themeMode by settings.themeMode.collectAsState()
    onThemeChanged(themeMode.isDark())
    val entitlements = koinInject<EntitlementRepository>()
    val syncManager = koinInject<SyncManager>()
    val sessionStore = koinInject<SessionStore>()
    val wordPackSync = koinInject<uz.abumme.harfgame.data.wordpack.WordPackSyncManager>()
    val packs = koinInject<WordPackRepository>()
    val resultLog = koinInject<ResultLog>()
    LaunchedEffect(Unit) {
        // Resolve the packs of the languages the player plays while Home is still on screen, so opening a game
        // never waits for one. Unplayed languages are left for their first open (keeps their memory unspent).
        launch {
            val played = resultLog.all().sortedByDescending { it.puzzleDay }.map { it.language }.distinct()
            for (lang in played) runCatching { packs.load(lang) }
        }
        entitlements.refresh() // reconcile with the store on launch (no-op offline)
        syncManager.bootstrap() // non-blocking background anonymous session and stats sync
        wordPackSync.syncAll()  // fetch newer vocab in the background (no-op offline)
    }
    LaunchedEffect(Unit) {
        // Keep the store identity in step with the Harf account: sign-in, link, switch, sign-out.
        // collectLatest: a new account cancels a still-pending check for the previous one.
        sessionStore.sessionFlow.filterNotNull().map { it.userId }.distinctUntilChanged()
            .collectLatest { entitlements.bindAccount(it) }
    }
    var onboarded by remember { mutableStateOf(settings.isOnboarded()) }
    val chosenPalette by settings.paletteId.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val accountKnown by entitlements.accountKnown.collectAsState()
    // A palette the account doesn't own (picked before themes were gated on this platform, or refunded)
    // draws as the free one. The choice itself stays saved, so it comes back if the purchase does.
    val palette = if (accountKnown) EntitlementGate.paletteToApply(chosenPalette, ents) else chosenPalette
    HarfTheme(paletteId = palette, mode = themeMode) {
        MarkStyleHost {
            if (!onboarded) {
                OnboardingIntro(onDone = {
                    settings.setOnboarded(true)
                    onboarded = true
                })
            } else {
                AppNavHost()
            }
        }
    }
}
