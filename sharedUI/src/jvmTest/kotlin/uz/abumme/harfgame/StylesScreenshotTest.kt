package uz.abumme.harfgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takahirom.roborazzi.captureRoboImage
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import uz.abumme.harfgame.theme.marks.markStyleFor
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StylesScreenshotTest {

    @Test
    fun three_styles_three_states() = runDesktopComposeUiTest {
        setContent {
            HarfTheme(paletteId = "newsprint") {
                val c = LocalHarfColors.current
                Column(
                    Modifier.width(320.dp).background(c.paper).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    for (id in HarfMarkStyleId.entries) {
                        Text(id.name, color = c.ink, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (m in listOf(Mark.CORRECT, Mark.PRESENT, Mark.ABSENT)) {
                                Box(
                                    Modifier.size(46.dp).border(1.dp, c.rule, RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { markStyleFor(id).Draw(m, c, Modifier.size(46.dp)) }
                            }
                        }
                    }
                }
            }
        }
        onRoot().captureRoboImage("roborazzi/mark_styles.png")
    }
}
