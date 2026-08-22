package uz.abumme.harfgame.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import uz.abumme.harfgame.settings.AppSettings

/**
 * Applies the active Harf palette app-wide: provides [LocalHarfColors] tokens and a
 * Material color scheme mapped from them. The active palette comes from [AppSettings]
 * and updates reactively; on first launch it is the default palette.
 */
@Composable
fun HarfTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val paletteId by settings.paletteId.collectAsState()
    HarfTheme(paletteId = paletteId, content = content)
}

/** Palette-id overload — handy for previews without DI. */
@Composable
fun HarfTheme(
    paletteId: String = HarfPalettes.Newsprint.id,
    content: @Composable () -> Unit,
) {
    val colors = HarfPalettes.byId(paletteId).colors
    val scheme = lightColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        secondary = colors.present,
        onSecondary = Color.White,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.card,
        onSurface = colors.ink,
        surfaceVariant = colors.paper2,
        onSurfaceVariant = colors.muted,
        error = colors.present,
        onError = Color.White,
        outline = colors.rule,
        outlineVariant = colors.rule,
    )
    CompositionLocalProvider(LocalHarfColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = harfTypography()) {
            Surface(color = colors.paper, contentColor = colors.ink, content = content)
        }
    }
}
