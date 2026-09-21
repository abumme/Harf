package uz.abumme.harfgame.admin.components

import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.selectors.focusVisible
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.loadFromLocalStorage
import com.varabyte.kobweb.silk.theme.colors.systemPreference
import org.jetbrains.compose.web.css.StyleScope

/**
 * The panel's design tokens, as CSS custom properties switched by Silk's color-mode class on `<html>`.
 *
 * The palette borrows the game's own vocabulary: the tile colors a guess is scored with (correct green, present
 * amber, absent grey) are the colors of account states here, so "active", "locked" and "disabled" read at a glance.
 */
object Tokens {
    const val BG = "var(--harf-bg)"
    const val SURFACE = "var(--harf-surface)"
    const val SURFACE_SUNKEN = "var(--harf-surface-sunken)"
    const val INK = "var(--harf-ink)"
    const val MUTED = "var(--harf-muted)"
    const val LINE = "var(--harf-line)"
    const val LINE_STRONG = "var(--harf-line-strong)"
    const val CORRECT = "var(--harf-correct)"
    const val CORRECT_INK = "var(--harf-correct-ink)"
    const val PRESENT = "var(--harf-present)"
    const val PRESENT_INK = "var(--harf-present-ink)"
    const val ABSENT = "var(--harf-absent)"
    const val ABSENT_INK = "var(--harf-absent-ink)"
    const val PRIMARY = "var(--harf-primary)"
    const val PRIMARY_HOVER = "var(--harf-primary-hover)"
    const val PRIMARY_INK = "var(--harf-primary-ink)"
    const val DANGER = "var(--harf-danger)"
    const val DANGER_SOFT = "var(--harf-danger-soft)"
    const val FOCUS = "var(--harf-focus)"
    const val SCRIM = "var(--harf-scrim)"

    /** Chart series colors, distinct in both color modes; the first three are the tile colors. */
    val CHART = listOf("var(--harf-chart-1)", "var(--harf-chart-2)", "var(--harf-chart-3)", "var(--harf-chart-4)", "var(--harf-chart-5)", "var(--harf-chart-6)")

    const val FONT = "Onest, \"Segoe UI\", system-ui, -apple-system, Roboto, \"Noto Sans\", sans-serif"

    val light = mapOf(
        "--harf-bg" to "#ECEFF3",
        "--harf-surface" to "#FFFFFF",
        "--harf-surface-sunken" to "#F5F7F9",
        "--harf-ink" to "#1C2330",
        "--harf-muted" to "#5A6473",
        "--harf-line" to "#D8DDE4",
        "--harf-line-strong" to "#B9C1CC",
        "--harf-correct" to "#3E8A58",
        "--harf-correct-ink" to "#FFFFFF",
        "--harf-present" to "#D4AE3C",
        "--harf-present-ink" to "#1C2330",
        "--harf-absent" to "#737C89",
        "--harf-absent-ink" to "#FFFFFF",
        "--harf-primary" to "#2E7449",
        "--harf-primary-hover" to "#255F3C",
        "--harf-primary-ink" to "#FFFFFF",
        "--harf-danger" to "#B23A2F",
        "--harf-danger-soft" to "#F8E6E3",
        "--harf-focus" to "#2D6CD0",
        "--harf-scrim" to "rgba(20, 26, 36, 0.45)",
        "--harf-chart-1" to "#3E8A58",
        "--harf-chart-2" to "#C99A1E",
        "--harf-chart-3" to "#2D6CD0",
        "--harf-chart-4" to "#B23A2F",
        "--harf-chart-5" to "#7A4FB5",
        "--harf-chart-6" to "#737C89",
    )

    val dark = mapOf(
        "--harf-bg" to "#11151C",
        "--harf-surface" to "#1A2029",
        "--harf-surface-sunken" to "#151A22",
        "--harf-ink" to "#E3E7ED",
        "--harf-muted" to "#9AA3B0",
        "--harf-line" to "#2B333F",
        "--harf-line-strong" to "#3D4755",
        "--harf-correct" to "#4F9D69",
        "--harf-correct-ink" to "#0E1511",
        "--harf-present" to "#CFAB45",
        "--harf-present-ink" to "#16130A",
        "--harf-absent" to "#5A6270",
        "--harf-absent-ink" to "#F1F3F6",
        "--harf-primary" to "#5DAE78",
        "--harf-primary-hover" to "#71BE8B",
        "--harf-primary-ink" to "#0C130E",
        "--harf-danger" to "#E57366",
        "--harf-danger-soft" to "#3A2220",
        "--harf-focus" to "#79A6F0",
        "--harf-scrim" to "rgba(0, 0, 0, 0.6)",
        "--harf-chart-1" to "#5DAE78",
        "--harf-chart-2" to "#D9B34A",
        "--harf-chart-3" to "#79A6F0",
        "--harf-chart-4" to "#E57366",
        "--harf-chart-5" to "#B292E0",
        "--harf-chart-6" to "#9AA3B0",
    )
}

/** Shorthand for CSS properties this panel sets by name (custom properties and `var()` values). */
fun Modifier.css(vararg properties: Pair<String, String>): Modifier = styleModifier {
    properties.forEach { (name, value) -> property(name, value) }
}

private fun StyleScope.tokens(values: Map<String, String>) = values.forEach { (name, value) -> property(name, value) }

/** Registers the tokens, the base typography and the initial color mode. */
fun initTheme(ctx: InitSilkContext) {
    // A mode picked with the sidebar switch wins; otherwise follow the system.
    val saved = try {
        ColorMode.loadFromLocalStorage()
    } catch (e: Throwable) {
        null
    }
    ctx.config.initialColorMode = saved ?: ColorMode.systemPreference
    ctx.stylesheet.registerStyleBase(".silk-light") { Modifier.styleModifier { tokens(Tokens.light) } }
    ctx.stylesheet.registerStyleBase(".silk-dark") { Modifier.styleModifier { tokens(Tokens.dark) } }
    ctx.stylesheet.registerStyleBase("html, body") {
        Modifier.css("height" to "100%", "background-color" to Tokens.BG)
    }
    ctx.stylesheet.registerStyleBase("body") {
        Modifier.css(
            "font-family" to Tokens.FONT,
            "font-size" to "15px",
            "line-height" to "1.5",
            "color" to Tokens.INK,
            "-webkit-font-smoothing" to "antialiased",
        )
    }
    ctx.stylesheet.registerStyleBase("h1, h2, h3, p") { Modifier.css("margin" to "0") }
    ctx.stylesheet.registerStyleBase("button, input, select, textarea") { Modifier.css("font" to "inherit") }
    ctx.stylesheet.registerStyleBase(":focus-visible") {
        Modifier.css("outline" to "2px solid ${Tokens.FOCUS}", "outline-offset" to "2px")
    }
}

val PageTitleStyle = CssStyle.base {
    Modifier.css(
        "font-size" to "26px",
        "line-height" to "1.2",
        "font-weight" to "700",
        "letter-spacing" to "-0.01em",
        "color" to Tokens.INK,
    )
}

val SectionTitleStyle = CssStyle.base {
    Modifier.css("font-size" to "17px", "line-height" to "1.3", "font-weight" to "650", "color" to Tokens.INK)
}

val MutedTextStyle = CssStyle.base {
    Modifier.css("color" to Tokens.MUTED, "font-size" to "14px")
}

/** A plain bordered panel on the page background; the one container shape in the panel. */
val PanelStyle = CssStyle.base {
    Modifier.css(
        "background-color" to Tokens.SURFACE,
        "border" to "1px solid ${Tokens.LINE}",
        "border-radius" to "8px",
    )
}

/** Buttons: primary (filled green), quiet (outlined) and danger (outlined red). */
val PrimaryButtonStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "inline-flex",
            "align-items" to "center",
            "justify-content" to "center",
            "gap" to "8px",
            "height" to "38px",
            "padding" to "0 16px",
            "border-radius" to "6px",
            "border" to "1px solid transparent",
            "background-color" to Tokens.PRIMARY,
            "color" to Tokens.PRIMARY_INK,
            "font-weight" to "600",
            "font-size" to "14px",
            "cursor" to "pointer",
            "white-space" to "nowrap",
            "text-decoration" to "none",
        )
    }
    hover { Modifier.css("background-color" to Tokens.PRIMARY_HOVER) }
    cssRule(":disabled") { Modifier.css("opacity" to "0.55", "cursor" to "not-allowed") }
}

val QuietButtonStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "inline-flex",
            "align-items" to "center",
            "justify-content" to "center",
            "gap" to "8px",
            "height" to "38px",
            "padding" to "0 14px",
            "border-radius" to "6px",
            "border" to "1px solid ${Tokens.LINE_STRONG}",
            "background-color" to Tokens.SURFACE,
            "color" to Tokens.INK,
            "font-weight" to "550",
            "font-size" to "14px",
            "cursor" to "pointer",
            "white-space" to "nowrap",
            "text-decoration" to "none",
        )
    }
    hover { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN) }
    cssRule(":disabled") { Modifier.css("opacity" to "0.55", "cursor" to "not-allowed") }
}

val DangerButtonStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "inline-flex",
            "align-items" to "center",
            "justify-content" to "center",
            "gap" to "8px",
            "height" to "38px",
            "padding" to "0 14px",
            "border-radius" to "6px",
            "border" to "1px solid ${Tokens.DANGER}",
            "background-color" to Tokens.SURFACE,
            "color" to Tokens.DANGER,
            "font-weight" to "600",
            "font-size" to "14px",
            "cursor" to "pointer",
            "white-space" to "nowrap",
        )
    }
    hover { Modifier.css("background-color" to Tokens.DANGER_SOFT) }
    cssRule(":disabled") { Modifier.css("opacity" to "0.55", "cursor" to "not-allowed") }
}

val TextInputStyle = CssStyle {
    base {
        Modifier.css(
            "width" to "100%",
            "height" to "40px",
            "padding" to "0 12px",
            "border-radius" to "6px",
            "border" to "1px solid ${Tokens.LINE_STRONG}",
            "background-color" to Tokens.SURFACE,
            "color" to Tokens.INK,
            "font-size" to "15px",
        )
    }
    focusVisible { Modifier.css("outline" to "2px solid ${Tokens.FOCUS}", "outline-offset" to "0", "border-color" to Tokens.FOCUS) }
    cssRule("[aria-invalid=\"true\"]") { Modifier.css("border-color" to Tokens.DANGER) }
    cssRule(":disabled") { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN, "color" to Tokens.MUTED) }
}

val FieldLabelStyle = CssStyle.base {
    Modifier.css("display" to "block", "font-size" to "14px", "font-weight" to "600", "margin-bottom" to "6px")
}

val FieldHintStyle = CssStyle.base {
    Modifier.css("font-size" to "13px", "color" to Tokens.MUTED, "margin-top" to "6px")
}

val FieldErrorStyle = CssStyle.base {
    Modifier.css("font-size" to "13px", "color" to Tokens.DANGER, "margin-top" to "6px", "font-weight" to "550")
}

/** A message above a form or table: errors in red, confirmations in the tile green. */
val NoticeStyle = CssStyle.base {
    Modifier.css(
        "padding" to "10px 14px",
        "border-radius" to "6px",
        "border-left" to "4px solid ${Tokens.LINE_STRONG}",
        "background-color" to Tokens.SURFACE_SUNKEN,
        "font-size" to "14px",
    )
}
