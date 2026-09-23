package uz.abumme.harfgame.theme.marks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.theme.HarfColors

/**
 * How feedback (correct/present/absent) is drawn on a tile or key. Each style must encode
 * state by shape as well as color so it stays legible in grayscale. Board and keyboard draw
 * via the active style from [LocalMarkStyle] — they never branch on the style themselves.
 */
interface MarkStyle {
    val id: HarfMarkStyleId

    @Composable
    fun Draw(mark: Mark, colors: HarfColors, modifier: Modifier)

    /**
     * Color for a glyph drawn on top of this style's mark, so the letter stays legible. Styles
     * that fill the tile solidly (see [FillMarkStyle]) return an on-mark color; outline/scribble
     * marks leave the tile ground showing, so the default ink is fine.
     */
    fun letterColor(mark: Mark, colors: HarfColors): Color = colors.ink
}

enum class HarfMarkStyleId { Scribble, Fill, Outline }

/** Active style; defaults to Scribble (the mockup look). Provided by MarkStyleHost. */
val LocalMarkStyle = staticCompositionLocalOf<MarkStyle> { ScribbleMarkStyle }

fun markStyleFor(id: HarfMarkStyleId): MarkStyle = when (id) {
    HarfMarkStyleId.Scribble -> ScribbleMarkStyle
    HarfMarkStyleId.Fill -> FillMarkStyle
    HarfMarkStyleId.Outline -> OutlineMarkStyle
}

fun markColor(mark: Mark, colors: HarfColors) = when (mark) {
    Mark.CORRECT -> colors.correct
    Mark.PRESENT -> colors.present
    Mark.ABSENT -> colors.absent
}
