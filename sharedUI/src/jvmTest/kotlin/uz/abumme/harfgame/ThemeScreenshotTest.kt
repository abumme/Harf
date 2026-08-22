package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.theme.HarfColors
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ThemeScreenshotTest {

    @Test
    fun each_palette_renders() {
        for (palette in HarfPalettes.all) {
            runDesktopComposeUiTest {
                setContent {
                    HarfTheme(paletteId = palette.id) { PaletteSample() }
                }
                onRoot().captureRoboImage("roborazzi/palette_${palette.id}.png")
            }
        }
    }
}

/** Self-contained design-system sample: wordmark + a feedback tile row + key row. */
@Composable
private fun PaletteSample() {
    val c = LocalHarfColors.current
    Column(
        modifier = Modifier
            .size(320.dp)
            .background(c.paper)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Harf.", color = c.ink, fontFamily = harfSerif(), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cell("S", c.correct, c); Cell("H", c.present, c); Cell("A", c.absent, c)
            Cell("H", c.correct, c); Cell("R", c.present, c)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in listOf("Q", "E", "R", "T", "Y")) Key(k, c)
        }
    }
}

@Composable
private fun Cell(letter: String, fill: Color, c: HarfColors) {
    Box(
        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(4.dp)).background(fill),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
}

@Composable
private fun Key(letter: String, c: HarfColors) {
    Box(
        modifier = Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(5.dp)).background(c.key),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = c.ink, fontSize = 13.sp) }
}
