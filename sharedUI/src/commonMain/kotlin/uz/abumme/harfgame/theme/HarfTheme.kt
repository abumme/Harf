package uz.abumme.harfgame.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Light or dark ground: follow the platform setting (the persisted default), or force one. */
enum class ThemeMode { System, Light, Dark }

/** Whether this mode draws the dark ground; [ThemeMode.System] follows the platform setting. */
@Composable
fun ThemeMode.isDark(): Boolean = isDark(systemDark = isSystemInDarkTheme())

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Applies a Harf palette app-wide: provides [LocalHarfColors] tokens and a Material color scheme
 * mapped from them, on the light or dark ground [mode] resolves to. The app passes the chosen
 * palette after the entitlement check and the saved mode (see `App`); previews pass a palette and
 * get the light ground.
 */
@Composable
fun HarfTheme(
    paletteId: String = HarfPalettes.Newsprint.id,
    mode: ThemeMode = ThemeMode.Light,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    val palette = HarfPalettes.byId(paletteId)
    val colors = if (dark) palette.dark else palette.colors
    // accent/present are dark fills on a light ground and light fills on a dark one
    val onFill = if (dark) colors.paper else Color.White
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = onFill,
        surfaceTint = colors.accent, // the factories default it to primary; copy() keeps the baseline
        secondary = colors.present,
        onSecondary = onFill,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.card,
        onSurface = colors.ink,
        surfaceVariant = colors.paper2,
        onSurfaceVariant = colors.muted,
        error = colors.danger,
        onError = colors.onDanger,
        outline = colors.rule,
        outlineVariant = colors.rule,
    )
    // Map Material's shape scale onto the Harf shape tokens so every Material surface — dialogs
    // (extraLarge), menus, cards — picks up the app's radii without a per-call `shape =`.
    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = HarfShapes.button,
        medium = HarfShapes.container,
        large = HarfShapes.container,
        extraLarge = HarfShapes.container,
    )
    CompositionLocalProvider(
        LocalHarfColors provides colors,
        LocalHarfPaletteId provides paletteId,
        LocalHarfShapes provides HarfShapes,
    ) {
        MaterialTheme(colorScheme = scheme, typography = harfTypography(), shapes = materialShapes) {
            Surface(color = colors.paper, contentColor = colors.ink, content = content)
        }
    }
}
