package uz.abumme.harfgame.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import org.koin.compose.koinInject
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
    val paletteId by settings.paletteId.collectAsState()
    val colors = LocalHarfColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
        Text("A daily word duel", color = colors.muted, fontSize = 14.sp)

        for ((id, label) in LANGUAGES) {
            Button(onClick = { onPlay(id) }, modifier = Modifier.width(220.dp)) { Text(label) }
        }

        OutlinedButton(onClick = onStats, modifier = Modifier.width(220.dp)) { Text("Statistics") }
        OutlinedButton(onClick = onSettings, modifier = Modifier.width(220.dp)) { Text("Settings") }

        OutlinedButton(
            onClick = {
                val list = HarfPalettes.all
                val i = list.indexOfFirst { it.id == paletteId }
                settings.setPaletteId(list[(i + 1) % list.size].id)
            },
            modifier = Modifier.width(220.dp),
        ) { Text("Theme — ${HarfPalettes.byId(paletteId).displayName}") }
    }
}
