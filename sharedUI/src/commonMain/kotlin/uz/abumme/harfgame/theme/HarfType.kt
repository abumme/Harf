package uz.abumme.harfgame.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import harf_game.sharedui.generated.resources.GolosText
import harf_game.sharedui.generated.resources.PTSerif_Bold
import harf_game.sharedui.generated.resources.PTSerif_Italic
import harf_game.sharedui.generated.resources.PTSerif_Regular
import harf_game.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** PT Serif — display / editorial headings. */
@Composable
fun harfSerif() = FontFamily(
    Font(Res.font.PTSerif_Regular, FontWeight.Normal),
    Font(Res.font.PTSerif_Bold, FontWeight.Bold),
    Font(Res.font.PTSerif_Italic, FontWeight.Normal, FontStyle.Italic),
)

/** Golos Text — UI / body (full Kazakh + Uzbek Cyrillic). */
@Composable
fun harfSans() = FontFamily(Font(Res.font.GolosText))

@Composable
fun harfTypography(): Typography {
    val serif = harfSerif()
    val sans = harfSans()
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = serif),
        displayMedium = b.displayMedium.copy(fontFamily = serif),
        displaySmall = b.displaySmall.copy(fontFamily = serif),
        headlineLarge = b.headlineLarge.copy(fontFamily = serif),
        headlineMedium = b.headlineMedium.copy(fontFamily = serif),
        headlineSmall = b.headlineSmall.copy(fontFamily = serif),
        titleLarge = b.titleLarge.copy(fontFamily = serif),
        titleMedium = b.titleMedium.copy(fontFamily = sans),
        titleSmall = b.titleSmall.copy(fontFamily = sans),
        bodyLarge = b.bodyLarge.copy(fontFamily = sans),
        bodyMedium = b.bodyMedium.copy(fontFamily = sans),
        bodySmall = b.bodySmall.copy(fontFamily = sans),
        labelLarge = b.labelLarge.copy(fontFamily = sans),
        labelMedium = b.labelMedium.copy(fontFamily = sans),
        labelSmall = b.labelSmall.copy(fontFamily = sans),
    )
}
