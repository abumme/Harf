package uz.abumme.harfgame.theme.marks

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.theme.HarfColors

/** Fast hand-drawn pencil marks: loop / underline / diagonal strike. */
object ScribbleMarkStyle : MarkStyle {
    override val id = HarfMarkStyleId.Scribble

    @Composable
    override fun Draw(mark: Mark, colors: HarfColors, modifier: Modifier) {
        val color = markColor(mark, colors)
        Canvas(modifier) {
            val w = size.width; val h = size.height
            val sw = size.minDimension * 0.08f
            when (mark) {
                Mark.CORRECT -> drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(sw))
                Mark.PRESENT -> drawLine(color, Offset(w * 0.22f, h * 0.8f), Offset(w * 0.78f, h * 0.8f), strokeWidth = sw)
                Mark.ABSENT -> drawLine(color, Offset(w * 0.22f, h * 0.8f), Offset(w * 0.78f, h * 0.2f), strokeWidth = sw)
            }
        }
    }
}

/** Clean geometric outlines: full border / underline bar / X. */
object OutlineMarkStyle : MarkStyle {
    override val id = HarfMarkStyleId.Outline

    @Composable
    override fun Draw(mark: Mark, colors: HarfColors, modifier: Modifier) {
        val color = markColor(mark, colors)
        Canvas(modifier) {
            val w = size.width; val h = size.height
            val sw = size.minDimension * 0.09f
            val inset = size.minDimension * 0.14f
            when (mark) {
                Mark.CORRECT -> drawRect(
                    color,
                    topLeft = Offset(inset, inset),
                    size = Size(w - 2 * inset, h - 2 * inset),
                    style = Stroke(sw),
                )
                Mark.PRESENT -> drawLine(color, Offset(w * 0.2f, h * 0.82f), Offset(w * 0.8f, h * 0.82f), strokeWidth = sw)
                Mark.ABSENT -> {
                    drawLine(color, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.76f, h * 0.76f), strokeWidth = sw)
                    drawLine(color, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.24f, h * 0.76f), strokeWidth = sw)
                }
            }
        }
    }
}

/** Solid fills: full / bottom-half / hollow. */
object FillMarkStyle : MarkStyle {
    override val id = HarfMarkStyleId.Fill

    // CORRECT fills the whole tile, so the glyph over it needs the light on-mark color.
    // PRESENT fills only the bottom half (the top stays on the tile ground) and ABSENT is a
    // hollow stroke, so ink stays legible for both.
    override fun letterColor(mark: Mark, colors: HarfColors) =
        if (mark == Mark.CORRECT) colors.onCorrect else colors.ink

    @Composable
    override fun Draw(mark: Mark, colors: HarfColors, modifier: Modifier) {
        val color = markColor(mark, colors)
        Canvas(modifier) {
            val w = size.width; val h = size.height
            when (mark) {
                Mark.CORRECT -> drawRect(color)
                Mark.PRESENT -> drawRect(color, topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                Mark.ABSENT -> drawRect(color, style = Stroke(size.minDimension * 0.06f))
            }
        }
    }
}
