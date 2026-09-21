package uz.abumme.harfgame.admin.components

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The three scores a letter tile can get in Harf, reused as the panel's state colors. */
enum class TileTone(val background: String, val ink: String) {
    CORRECT(Tokens.CORRECT, Tokens.CORRECT_INK),
    PRESENT(Tokens.PRESENT, Tokens.PRESENT_INK),
    ABSENT(Tokens.ABSENT, Tokens.ABSENT_INK),

    /** An empty board tile: outlined, not yet scored. */
    OPEN(Tokens.SURFACE, Tokens.INK),
}

val WordmarkStyle = CssStyle.base {
    Modifier.css("display" to "flex", "gap" to "6px", "align-items" to "center")
}

val WordmarkTileStyle = CssStyle.base {
    Modifier.css(
        "display" to "grid",
        "place-items" to "center",
        "font-weight" to "800",
        "line-height" to "1",
        "border-radius" to "4px",
        "user-select" to "none",
    )
}

val BadgeStyle = CssStyle.base {
    Modifier.css(
        "display" to "inline-flex",
        "align-items" to "center",
        "gap" to "6px",
        "height" to "24px",
        "padding" to "0 9px",
        "border-radius" to "4px",
        "font-size" to "13px",
        "font-weight" to "600",
        "white-space" to "nowrap",
    )
}

/**
 * "HARF" as a row of scored tiles, the way a guess looks on the board: the panel's one bold element.
 * [tileSize] is the edge of each tile in pixels.
 */
@Composable
fun Wordmark(tileSize: Int, modifier: Modifier = Modifier) {
    val letters = listOf("H" to TileTone.CORRECT, "A" to TileTone.PRESENT, "R" to TileTone.ABSENT, "F" to TileTone.CORRECT)
    Div(WordmarkStyle.toModifier().then(modifier).toAttrs { attr("role", "img"); attr("aria-label", "Harf") }) {
        letters.forEach { (letter, tone) ->
            Span(
                WordmarkTileStyle.toModifier().css(
                    "width" to "${tileSize}px",
                    "height" to "${tileSize}px",
                    "font-size" to "${(tileSize * 0.55).toInt()}px",
                    "background-color" to tone.background,
                    "color" to tone.ink,
                ).toAttrs { attr("aria-hidden", "true") },
            ) { Text(letter) }
        }
    }
}

/** A small state label in a tile color. [OPEN] is outlined. */
@Composable
fun Badge(text: String, tone: TileTone, modifier: Modifier = Modifier) {
    val outline = if (tone == TileTone.OPEN) "1px solid ${Tokens.LINE_STRONG}" else "1px solid transparent"
    Span(
        BadgeStyle.toModifier()
            .css("background-color" to tone.background, "color" to tone.ink, "border" to outline)
            .then(modifier)
            .toAttrs(),
    ) { Text(text) }
}
