package uz.abumme.harfgame.feature.cellstyles

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_skip
import harf_game.sharedui.generated.resources.style_prompt_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import uz.abumme.harfgame.theme.marks.LocalMarkStyle
import uz.abumme.harfgame.theme.marks.markStyleFor

/** Provides the active [LocalMarkStyle] app-wide and shows the one-time preference prompt. */
@Composable
fun MarkStyleHost(content: @Composable () -> Unit) {
    val controller = koinInject<StyleExperimentController>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { controller.onAppOpen() }

    val active by controller.activeStyle.collectAsState()
    val phase by controller.phase.collectAsState()

    CompositionLocalProvider(LocalMarkStyle provides markStyleFor(active)) {
        content()
        if (phase == ExperimentPhase.PromptPending) {
            StylePrompt(
                onChoose = { scope.launch { controller.choose(it) } },
                onDismiss = { scope.launch { controller.dismissPrompt() } },
            )
        }
    }
}

/** Small sample of a style: correct / present / absent marks. */
@Composable
fun StylePreview(styleId: HarfMarkStyleId, modifier: Modifier = Modifier) {
    val colors = LocalHarfColors.current
    val style = markStyleFor(styleId)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in listOf(Mark.CORRECT, Mark.PRESENT, Mark.ABSENT)) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(32.dp).border(1.dp, colors.rule, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) { style.Draw(m, colors, Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun StylePrompt(onChoose: (HarfMarkStyleId) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalHarfColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_skip)) } },
        title = { Text(stringResource(Res.string.style_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in HarfMarkStyleId.entries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StylePreview(id)
                        TextButton(onClick = { onChoose(id) }, modifier = Modifier.padding(start = 8.dp)) {
                            Text(id.name, color = colors.ink, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
    )
}
