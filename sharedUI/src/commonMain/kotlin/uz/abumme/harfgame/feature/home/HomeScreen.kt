package uz.abumme.harfgame.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.home_tagline
import harf_game.sharedui.generated.resources.home_theme
import harf_game.sharedui.generated.resources.settings_title
import harf_game.sharedui.generated.resources.statistics
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif

private val LANGUAGES = listOf(
    "uz-latn" to "Oʻzbekcha",
    "ru" to "Русский",
    "en" to "English",
    "kk" to "Қазақша",
)

@Composable
fun HomeScreen(onPlay: (String) -> Unit = {}, onStats: () -> Unit = {}, onSettings: () -> Unit = {}) {
    val settings = koinInject<AppSettings>()
    val entitlements = koinInject<EntitlementRepository>()
    val controller = koinInject<PurchaseController>()
    val paletteId by settings.paletteId.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val colors = LocalHarfColors.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      // Centered when it fits; scrolls (never clips) when content exceeds the viewport —
      // heightIn(min = maxHeight) gives Center room to work while allowing overflow to grow.
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = buildAnnotatedString {
                append("Harf")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            color = colors.ink,
            fontFamily = harfSerif(),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(Res.string.home_tagline), color = colors.muted, fontSize = 14.sp)

        for ((id, label) in LANGUAGES) {
            Button(onClick = { onPlay(id) }, modifier = Modifier.width(220.dp)) { Text(label) }
        }

        OutlinedButton(onClick = onStats, modifier = Modifier.width(220.dp)) { Text(stringResource(Res.string.statistics)) }
        OutlinedButton(onClick = onSettings, modifier = Modifier.width(220.dp)) { Text(stringResource(Res.string.settings_title)) }

        OutlinedButton(
            onClick = {
                // cycle only through applicable palettes; unowned themes are bought on the paywall
                val ids = HarfPalettes.all
                    .filter { EntitlementGate.canApplyTheme(it.id, ents, controller.isAvailable) }
                    .map { it.id }
                val i = ids.indexOf(paletteId).coerceAtLeast(0)
                settings.setPaletteId(ids[(i + 1) % ids.size])
            },
            modifier = Modifier.width(220.dp),
        ) { Text(stringResource(Res.string.home_theme, HarfPalettes.byId(paletteId).displayName)) }
      }
    }
}
