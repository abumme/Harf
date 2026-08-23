package uz.abumme.harfgame.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.legend_absent
import harf_game.sharedui.generated.resources.legend_correct
import harf_game.sharedui.generated.resources.legend_present
import org.jetbrains.compose.resources.stringResource
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.LocalMarkStyle

/**
 * The feedback legend, drawn in the active [LocalMarkStyle]. Reused by the first-run intro and
 * the in-game help so they always agree and track style/palette changes.
 */
@Composable
fun MarkLegend(modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = LocalHarfColors.current
    val style = LocalMarkStyle.current
    val cell = if (compact) 22.dp else 30.dp
    val items = listOf(
        Mark.CORRECT to stringResource(Res.string.legend_correct),
        Mark.PRESENT to stringResource(Res.string.legend_present),
        Mark.ABSENT to stringResource(Res.string.legend_absent),
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
        for ((mark, label) in items) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                style.Draw(mark, colors, Modifier.size(cell))
                Text(
                    label,
                    color = colors.muted,
                    fontSize = if (compact) 9.sp else 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
