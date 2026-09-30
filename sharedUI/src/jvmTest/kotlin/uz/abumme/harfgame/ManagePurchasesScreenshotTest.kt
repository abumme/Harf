package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.feature.purchases.ManagePurchasesContent
import uz.abumme.harfgame.feature.purchases.ManagePurchasesState
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ManagePurchasesScreenshotTest {

    private fun shot(name: String, state: ManagePurchasesState) = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Box(Modifier.size(390.dp, 780.dp).background(c.paper)) {
                    ManagePurchasesContent(state = state)
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/$name.png")
    }

    @Test
    fun manage_purchases_owned() = shot(
        "manage_purchases_owned",
        ManagePurchasesState(
            entitlements = Entitlements(lifetime = true, ownedThemes = setOf("theme_press", "theme_ink")),
        ),
    )

    @Test
    fun manage_purchases_empty() = shot("manage_purchases_empty", ManagePurchasesState())
}
