package uz.abumme.harfgame

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import uz.abumme.harfgame.theme.HarfColors
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.ThemeMode
import uz.abumme.harfgame.theme.isDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HarfTheme resolves the theme mode to the edition's light or dark tokens and Material scheme. */
@OptIn(ExperimentalTestApi::class)
class HarfThemeModeTest {

    private val press = HarfPalettes.Press

    @Test
    fun system_follows_the_platform_and_explicit_modes_override_it() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.System.isDark(systemDark))
            assertFalse(ThemeMode.Light.isDark(systemDark))
            assertTrue(ThemeMode.Dark.isDark(systemDark))
        }
    }

    @Test
    fun mode_switches_the_edition_ground_live() = runDesktopComposeUiTest {
        var mode by mutableStateOf(ThemeMode.Dark)
        var tokens: HarfColors? = null
        var background = Color.Unspecified
        var onPrimary = Color.Unspecified
        setContent {
            HarfTheme(paletteId = press.id, mode = mode) {
                tokens = LocalHarfColors.current
                background = MaterialTheme.colorScheme.background
                onPrimary = MaterialTheme.colorScheme.onPrimary
            }
        }
        assertEquals(press.dark, tokens)
        assertEquals(press.dark.paper, background, "dark Material scheme")
        assertEquals(press.dark.paper, onPrimary, "text over the light accent fill is the dark ground")

        mode = ThemeMode.Light
        waitForIdle()
        assertEquals(press.colors, tokens)
        assertEquals(press.colors.paper, background)
        assertEquals(Color.White, onPrimary)
    }
}
