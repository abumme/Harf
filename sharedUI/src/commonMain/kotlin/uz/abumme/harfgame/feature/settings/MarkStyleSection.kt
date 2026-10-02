package uz.abumme.harfgame.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.abumme.harfgame.feature.cellstyles.StylePreview
import uz.abumme.harfgame.feature.onboarding.MarkLegend
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfShapes
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import uz.abumme.harfgame.theme.marks.LocalMarkStyle
import uz.abumme.harfgame.theme.marks.markStyleFor

/**
 * The mark-style picker with the legend under it, so the choice is explained where it is made: the legend
 * draws in the active style and palette. DI-free; the screen supplies the active style and the setter.
 */
@Composable
fun MarkStyleSection(active: HarfMarkStyleId, onSelect: (HarfMarkStyleId) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHarfColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (id in HarfMarkStyleId.entries) {
            val selected = id == active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(id) }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.accent else colors.rule,
                        shape = LocalHarfShapes.current.swatch,
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StylePreview(id)
                Text(
                    id.name,
                    color = colors.ink,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                )
            }
        }
        // The legend moved here from the game screen (see change consistent-game-layout). It draws in
        // [active] itself, so it follows a pick even before the app-wide style catches up.
        CompositionLocalProvider(LocalMarkStyle provides markStyleFor(active)) {
            MarkLegend(Modifier.testTag("mark-legend").padding(top = 4.dp, start = 4.dp))
        }
    }
}
