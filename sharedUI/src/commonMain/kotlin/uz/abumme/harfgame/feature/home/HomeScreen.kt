package uz.abumme.harfgame.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel { HomeViewModel() }) {
    val settings = koinInject<AppSettings>()
    val paletteId by settings.paletteId.collectAsState()
    val colors = LocalHarfColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = buildAnnotatedString {
                append("Harf")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            color = colors.ink,
            fontFamily = harfSerif(),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Palette — ${HarfPalettes.byId(paletteId).displayName}",
            color = colors.muted,
            fontSize = 14.sp,
        )

        // feedback swatches: shape (mark) comes later; here the color roles
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(colors.correct, "correct")
            Swatch(colors.present, "present")
            Swatch(colors.absent, "absent")
        }

        OutlinedButton(onClick = {
            val list = HarfPalettes.all
            val i = list.indexOfFirst { it.id == paletteId }
            settings.setPaletteId(list[(i + 1) % list.size].id)
        }) { Text("Next palette") }

        Button(onClick = { vm.onAction(HomeAction.Poke) }) { Text("Play (soon)") }
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color, label: String) {
    val colors = LocalHarfColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        )
        Text(label, color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
