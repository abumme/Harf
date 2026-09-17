package uz.abumme.harfgame.admin.components

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLock
import com.varabyte.kobweb.silk.components.icons.lucide.LucideRepeat
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.calendar.DayLock
import uz.abumme.harfgame.admin.calendar.GridCell
import uz.abumme.harfgame.admin.calendar.monthGrid
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource

/** Seven equal columns; below ~720 px the grid scrolls sideways inside its panel instead of squeezing the words. */
val CalendarGridStyle = CssStyle.base {
    Modifier.css(
        "display" to "grid",
        "grid-template-columns" to "repeat(7, minmax(96px, 1fr))",
        "min-width" to "700px",
        "gap" to "1px",
        "background-color" to Tokens.LINE,
    )
}

val CalendarWeekdayStyle = CssStyle.base {
    Modifier.css(
        "padding" to "8px 10px",
        "font-size" to "12px",
        "font-weight" to "600",
        "letter-spacing" to "0.04em",
        "text-transform" to "uppercase",
        "color" to Tokens.MUTED,
        "background-color" to Tokens.SURFACE_SUNKEN,
    )
}

val CalendarCellStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "flex",
            "flex-direction" to "column",
            "align-items" to "stretch",
            "gap" to "5px",
            "min-height" to "104px",
            "padding" to "8px 9px",
            "background-color" to Tokens.SURFACE,
            "color" to Tokens.INK,
            "border" to "none",
            "border-top" to "3px solid transparent",
            "text-align" to "left",
            "font" to "inherit",
            "min-width" to "0",
        )
    }
    cssRule("[data-out-of-month]") { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN, "color" to Tokens.MUTED) }
    cssRule("[data-today]") { Modifier.css("border-top-color" to Tokens.CORRECT) }
    cssRule("[data-manual]") { Modifier.css("box-shadow" to "inset 3px 0 0 ${Tokens.CORRECT}") }
    hover { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN) }
}

val CalendarCellWordStyle = CssStyle.base {
    Modifier.css("font-weight" to "650", "font-size" to "15px", "line-height" to "1.25", "overflow-wrap" to "anywhere")
}

val CalendarCellMetaStyle = CssStyle.base {
    Modifier.css("font-size" to "12px", "color" to Tokens.MUTED, "line-height" to "1.3", "overflow-wrap" to "anywhere")
}

/**
 * A month of one daily-word calendar: a cell per day with its word (both scripts for Uzbek), how it was picked, the
 * repeat marker with the previous use, and the lock. An unlocked day is a button that opens the picker ([onPick]); a
 * locked one (past, today, tomorrow in the calendar's timezone, confirmed by the server's flag) has no controls.
 */
@Composable
fun CalendarMonthGrid(
    month: YearMonth,
    today: LocalDate,
    days: Map<String, DayDto>,
    onPick: (LocalDate, DayDto?) -> Unit,
) {
    Div(PanelStyle.toModifier().css("overflow-x" to "auto").toAttrs()) {
        Div(CalendarGridStyle.toModifier().toAttrs {
            attr("role", "group")
            attr("aria-label", Strings.Calendar.month(month))
        }) {
            Strings.Calendar.weekdays.forEach { name ->
                Div(CalendarWeekdayStyle.toModifier().toAttrs { attr("aria-hidden", "true") }) { Text(name) }
            }
            monthGrid(month, today).flatten().forEach { cell -> DayCell(cell, days[cell.date.toString()], onPick) }
        }
    }
}

@Composable
private fun DayCell(cell: GridCell, day: DayDto?, onPick: (LocalDate, DayDto?) -> Unit) {
    val locked = cell.lock.locked || day?.locked == true
    val label = buildString {
        append(Strings.Calendar.shortDay(cell.date.toString()))
        day?.let { append(": ").append(it.text); it.textCyrl?.let { cyrl -> append(" / ").append(cyrl) } }
        if (locked) append(" — ").append(Strings.Calendar.LOCKED.lowercase())
    }
    val content: @Composable () -> Unit = {
        Div(Modifier.css("display" to "flex", "align-items" to "center", "justify-content" to "space-between", "gap" to "6px").toAttrs()) {
            Span(Modifier.css("font-size" to "13px", "font-weight" to "600", "font-variant-numeric" to "tabular-nums").toAttrs()) {
                Text(cell.date.day.toString())
            }
            if (locked) {
                Span(Modifier.css("display" to "inline-flex", "color" to Tokens.MUTED).toAttrs { attr("title", Strings.Calendar.LOCKED) }) {
                    LucideLock(Modifier.css("width" to "14px", "height" to "14px"))
                }
            }
        }
        if (day == null) {
            Span(CalendarCellMetaStyle.toModifier().toAttrs()) { Text(if (cell.inMonth) Strings.Calendar.EMPTY_DAY else "") }
        } else {
            Span(CalendarCellWordStyle.toModifier().toAttrs()) { Text(day.text) }
            day.textCyrl?.let { Span(CalendarCellWordStyle.toModifier().css("font-weight" to "550").toAttrs()) { Text(it) } }
            Div(Modifier.css("display" to "flex", "flex-wrap" to "wrap", "gap" to "4px", "margin-top" to "auto").toAttrs()) {
                Badge(Strings.Calendar.source(day.source), if (day.source == DaySource.MANUAL) TileTone.CORRECT else TileTone.OPEN)
                if (day.isRepeat) {
                    Span(Modifier.toAttrs { attr("title", Strings.Calendar.repeatSince(day.lastUsed)) }) {
                        Badge(Strings.Calendar.REPEAT, TileTone.PRESENT)
                    }
                }
            }
            if (day.isRepeat && day.lastUsed != null) {
                Span(CalendarCellMetaStyle.toModifier().toAttrs()) {
                    LucideRepeat(Modifier.css("width" to "11px", "height" to "11px", "vertical-align" to "-1px", "margin-right" to "3px"))
                    Text(Strings.Calendar.shortDay(day.lastUsed!!))
                }
            }
            day.pickedBy?.let { Span(CalendarCellMetaStyle.toModifier().toAttrs()) { Text(Strings.Calendar.pickedBy(it.displayName ?: it.username)) } }
        }
    }
    val modifier = CalendarCellStyle.toModifier()
    if (locked) {
        Div(modifier.toAttrs {
            attr("aria-label", label)
            if (!cell.inMonth) attr("data-out-of-month", "")
            if (cell.lock == DayLock.TODAY) attr("data-today", "")
            if (day?.source == DaySource.MANUAL) attr("data-manual", "")
        }) { content() }
    } else {
        Button(modifier.css("cursor" to "pointer").toAttrs {
            type(ButtonType.Button)
            attr("aria-label", label)
            if (!cell.inMonth) attr("data-out-of-month", "")
            if (day?.source == DaySource.MANUAL) attr("data-manual", "")
            onClick { onPick(cell.date, day) }
        }) { content() }
    }
}
