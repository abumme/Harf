package uz.abumme.harfgame.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * The app-wide shape scale. Corner radii come from the "After" mockup
 * (`docs/ui-review/before-after.html`); screens read these tokens rather than literal
 * radii so the mockup's shapes are never silently flattened to one default.
 *
 * Tile and key shapes are intentionally NOT here — they stay owned by the board/keyboard.
 */
@Immutable
data class HarfShapeScale(
    val button: RoundedCornerShape = RoundedCornerShape(10.dp),
    val container: RoundedCornerShape = RoundedCornerShape(12.dp),
    val row: RoundedCornerShape = RoundedCornerShape(11.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val swatch: RoundedCornerShape = RoundedCornerShape(8.dp),
)

val HarfShapes = HarfShapeScale()

val LocalHarfShapes = staticCompositionLocalOf { HarfShapes }
