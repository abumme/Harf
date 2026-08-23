package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.feature.onboarding.OnboardingIntro
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.LocalMarkStyle
import uz.abumme.harfgame.theme.marks.ScribbleMarkStyle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class OnboardingScreenshotTest {

    @Test
    fun intro_with_legend() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                CompositionLocalProvider(LocalMarkStyle provides ScribbleMarkStyle) {
                    val c = LocalHarfColors.current
                    Box(Modifier.size(320.dp, 440.dp).background(c.paper)) {
                        OnboardingIntro(onDone = {})
                    }
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/onboarding_intro.png")
    }
}
