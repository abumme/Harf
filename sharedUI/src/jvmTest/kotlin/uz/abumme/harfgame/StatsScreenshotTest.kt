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
import uz.abumme.harfgame.data.stats.PlayerStats
import uz.abumme.harfgame.data.stats.StreakStats
import uz.abumme.harfgame.feature.stats.StatsContent
import uz.abumme.harfgame.feature.stats.StatsEntry
import uz.abumme.harfgame.feature.stats.StatsState
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StatsScreenshotTest {

    private val state = StatsState(
        entries = listOf(
            StatsEntry("uz-latn", "Oʻzbekcha", PlayerStats(12, 0.92f, mapOf(3 to 6, 4 to 4, 5 to 1)), StreakStats(5, 9)),
            StatsEntry("ru", "Русский", PlayerStats(8, 0.75f, mapOf(3 to 3, 4 to 3)), StreakStats(2, 4)),
            StatsEntry("en", "English", PlayerStats(20, 0.85f, mapOf(2 to 2, 3 to 10, 4 to 5)), StreakStats(11, 11)),
        ),
    )

    @Test
    fun stats_screen() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Box(Modifier.size(360.dp, 440.dp).background(c.paper)) {
                    StatsContent(state)
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/stats_screen.png")
    }
}
