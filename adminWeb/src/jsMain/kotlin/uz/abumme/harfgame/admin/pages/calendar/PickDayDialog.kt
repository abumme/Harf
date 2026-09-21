package uz.abumme.harfgame.admin.pages.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLElement
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.calendar.calendarErrorMessage
import uz.abumme.harfgame.admin.calendar.candidateUsage
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.FormDialog
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarParams
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.api.ApiResult

private const val SEARCH_DEBOUNCE_MILLIS = 300L
private const val CANDIDATES_SHOWN = 50

/**
 * Picks the word of an unlocked day: search the calendar's answer pool (never-used words first, each with its last use or
 * the day it is scheduled on) and pick one; a manual day can also go back to automatic. A refused pick explains itself,
 * e.g. with the days the word was already used on.
 */
@Composable
fun PickDayDialog(calendar: String, day: LocalDate, current: DayDto?, onDismiss: () -> Unit, onChanged: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<CalendarCandidateDto>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(search) {
        if (search.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        loading = true
        val query = queryString(
            CalendarParams.DAY to day.toString(),
            CalendarParams.Q to search.trim().ifEmpty { null },
            CalendarParams.SIZE to CANDIDATES_SHOWN.toString(),
        )
        when (val result = AdminApp.api.calendarCandidates(calendar, query)) {
            is ApiResult.Success -> {
                candidates = result.data.items
                total = result.data.total
            }
            is ApiResult.Error -> error = generalMessage(result)
        }
        loading = false
    }

    fun run(action: suspend () -> ApiResult<DayDto>, success: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val result = action()) {
                is ApiResult.Success -> onChanged(success)
                is ApiResult.Error -> error = calendarErrorMessage(result)
            }
            busy = false
        }
    }

    FormDialog("pick-day", Strings.Calendar.pickTitle(calendar, day.toString()), onDismiss, busy = busy, wide = true) {
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
            if (current != null) {
                Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "flex-wrap" to "wrap").toAttrs()) {
                    Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Calendar.CURRENT) }
                    Span(Modifier.css("font-weight" to "650", "font-size" to "16px").toAttrs()) {
                        Text(current.textCyrl?.let { "${current.text} / $it" } ?: current.text)
                    }
                    Badge(Strings.Calendar.source(current.source), if (current.source == DaySource.MANUAL) TileTone.CORRECT else TileTone.OPEN)
                    if (current.isRepeat) Badge(Strings.Calendar.repeatSince(current.lastUsed), TileTone.PRESENT)
                    if (current.source == DaySource.MANUAL) {
                        ActionButton(
                            Strings.Calendar.UNPICK,
                            { run({ AdminApp.api.unpickDay(calendar, day.toString()) }, Strings.Calendar.UNPICKED) },
                            ButtonKind.QUIET,
                            enabled = !busy,
                            modifier = Modifier.css("margin-left" to "auto", "height" to "32px"),
                        )
                    }
                }
            }
            TextField("pick-day-search", Strings.Calendar.PICK_SEARCH, search, { search = it }, autoComplete = "off")
            Notice(error, NoticeTone.ERROR)
            if (!loading && candidates.isEmpty()) {
                P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Calendar.NO_CANDIDATES) }
            }
            Ul(Modifier.css(
                "margin" to "0", "padding" to "0", "list-style" to "none", "max-height" to "46vh", "overflow-y" to "auto",
                "border" to "1px solid ${Tokens.LINE}", "border-radius" to "6px",
            ).toAttrs { attr("aria-busy", loading.toString()) }) {
                candidates.forEach { candidate ->
                    val isCurrent = current != null && (candidate.wordId ?: candidate.pairId) == (current.wordId ?: current.pairId) &&
                        current.source == DaySource.MANUAL
                    Li(Modifier.css(
                        "display" to "flex", "align-items" to "center", "gap" to "12px", "padding" to "9px 12px",
                        "border-bottom" to "1px solid ${Tokens.LINE}",
                    ).toAttrs()) {
                        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px", "min-width" to "0", "flex" to "1").toAttrs()) {
                            Span(Modifier.css("font-weight" to "650").toAttrs()) {
                                Text(candidate.textCyrl?.let { "${candidate.text} / $it" } ?: candidate.text)
                            }
                            Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(candidateUsage(candidate)) }
                        }
                        ActionButton(
                            Strings.Calendar.PICK,
                            {
                                val request = if (calendar == DailyCalendars.UZ) PickDayRequest(pairId = candidate.pairId) else PickDayRequest(wordId = candidate.wordId)
                                run({ AdminApp.api.pickDay(calendar, day.toString(), request) }, Strings.Calendar.PICKED)
                            },
                            ButtonKind.PRIMARY,
                            enabled = !busy && !isCurrent,
                            modifier = Modifier.css("height" to "32px", "padding" to "0 12px"),
                        )
                    }
                }
            }
            if (total > candidates.size) {
                P(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) {
                    Text(Strings.Common.range(if (candidates.isEmpty()) 0L else 1L, candidates.size.toLong(), total))
                }
            }
            Div(Modifier.css("display" to "flex", "justify-content" to "flex-end").toAttrs()) {
                ActionButton(Strings.Common.BACK, onDismiss, ButtonKind.QUIET, enabled = !busy, id = "pick-day-close")
            }
        }
    }
    LaunchedEffect(Unit) { (document.getElementById("pick-day-search") as? HTMLElement)?.focus() }
}
