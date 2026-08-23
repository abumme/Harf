package uz.abumme.harfgame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import uz.abumme.harfgame.feature.cellstyles.MarkStyleHost
import uz.abumme.harfgame.feature.onboarding.OnboardingIntro
import uz.abumme.harfgame.navigation.AppNavHost
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfTheme

/**
 * App root: applies the Harf theme (active palette from settings) and hosts the
 * navigation graph. [onThemeChanged] lets platform wrappers sync system-bar style;
 * Harf editions are light, so it reports `isDark = false`.
 */
@Composable
fun App(onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}) {
    onThemeChanged(false)
    val settings = koinInject<AppSettings>()
    var onboarded by remember { mutableStateOf(settings.isOnboarded()) }
    HarfTheme(settings) {
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
