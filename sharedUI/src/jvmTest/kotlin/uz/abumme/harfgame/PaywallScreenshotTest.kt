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
import uz.abumme.harfgame.billing.Offerings
import uz.abumme.harfgame.billing.StoreItem
import uz.abumme.harfgame.feature.paywall.PaywallContent
import uz.abumme.harfgame.feature.paywall.PaywallPhase
import uz.abumme.harfgame.feature.paywall.PaywallState
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PaywallScreenshotTest {

    @Test
    fun paywall_ready_newsprint() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Box(Modifier.size(390.dp, 780.dp).background(c.paper)) {
                    PaywallContent(
                        state = PaywallState(
                            phase = PaywallPhase.Ready,
                            offerings = Offerings(
                                lifetime = StoreItem(
                                    id = "harf_founder",
                                    title = "Harf Founder",
                                    priceLabel = "49 000 UZS",
                                ),
                                themes = listOf(
                                    StoreItem(id = "theme_press", title = "Матбуот (Press)", priceLabel = "12 000 UZS"),
                                    StoreItem(id = "theme_ink", title = "Сиёҳдон (Ink)", priceLabel = "12 000 UZS"),
                                    StoreItem(id = "theme_blueprint", title = "Чизма (Blueprint)", priceLabel = "12 000 UZS"),
                                    StoreItem(id = "theme_schoolbook", title = "Дарслик (Schoolbook)", priceLabel = "12 000 UZS"),
                                ),
                            ),
                            entitlements = Entitlements(ownedThemes = setOf("theme_press")),
                        ),
                    )
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/paywall_ready.png")
    }

    @Test
    fun paywall_unavailable_newsprint() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Box(Modifier.size(390.dp, 780.dp).background(c.paper)) {
                    PaywallContent(
                        state = PaywallState(
                            phase = PaywallPhase.Unavailable,
                        ),
                    )
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/paywall_unavailable.png")
    }
}
