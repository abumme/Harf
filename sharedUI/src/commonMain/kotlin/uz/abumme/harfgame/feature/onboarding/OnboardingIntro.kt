package uz.abumme.harfgame.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_skip
import harf_game.sharedui.generated.resources.action_start
import harf_game.sharedui.generated.resources.onboarding_tagline
import org.jetbrains.compose.resources.stringResource
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif

/**
 * First-run introduction: the goal + the feedback legend, shown once. Also reused as the
 * in-game how-to. Skippable; [onDone] proceeds into the app / dismisses.
 */
@Composable
fun OnboardingIntro(onDone: () -> Unit) {
    val colors = LocalHarfColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text(
            buildAnnotatedString {
                append("Harf")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            color = colors.ink,
            fontFamily = harfSerif(),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.onboarding_tagline),
            color = colors.ink,
            fontSize = 18.sp,
            fontFamily = harfSerif(),
        )
        MarkLegend()
        Button(onClick = onDone, modifier = Modifier.width(200.dp)) { Text(stringResource(Res.string.action_start)) }
        TextButton(onClick = onDone) { Text(stringResource(Res.string.action_skip), color = colors.muted) }
    }
}
