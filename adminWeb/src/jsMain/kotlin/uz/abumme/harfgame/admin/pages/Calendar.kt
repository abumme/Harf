package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.icons.lucide.LucideBellRing
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronLeft
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronRight
import com.varabyte.kobweb.silk.components.icons.lucide.LucideTable2
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.calendar.CalendarTableState
import uz.abumme.harfgame.admin.calendar.CalendarViewState
import uz.abumme.harfgame.admin.calendar.todayIn
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.CalendarMonthGrid
import uz.abumme.harfgame.admin.components.IconButton
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.QuietButtonStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SectionTitleStyle
import uz.abumme.harfgame.admin.components.StateTabs
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.pages.calendar.PickDayDialog
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.js.Date

/**
 * `/calendar`: the ADMIN's month view of each daily-word calendar. Every day shows its word, how it was picked, the
 * repeat marker and the lock; an unlocked day opens the picker. Replaced manual picks are listed above the month until
 * dismissed. The calendar and month live in the query string.
 */
@Page
@Composable
fun CalendarPage() {
    RequireSession(PageAccess.CALENDAR) { me ->
        AdminShell(me, NavSection.CALENDAR) { CalendarMonths() }
    }
}

private data class PickTarget(val day: LocalDate, val current: DayDto?)

@Composable
private fun CalendarMonths() {
    val ctx = rememberPageContext()
    var state by remember { mutableStateOf(CalendarViewState.fromParams(ctx.route.params, todayIn(DailyCalendars.EN, Date.now()))) }
    var notice by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(state) { ctx.router.navigateTo(Routes.CALENDAR + state.toRouteQuery(), UpdateHistoryMode.REPLACE) }

    PageHeader(Strings.Calendar.TITLE, Strings.Calendar.SUBTITLE) {
        Anchor(Routes.CALENDAR_TABLE + CalendarTableState(state.calendar).toRouteQuery(), QuietButtonStyle.toModifier().toAttrs()) {
            LucideTable2()
            Text(Strings.Calendar.TAB_TABLE)
        }
    }
    Notices(reload)
    notice?.let { (text, tone) -> Notice(text, tone, Modifier.css("margin-bottom" to "14px")) }
    StateTabs(
        options = DailyCalendars.ALL.map { it to Strings.Calendar.calendar(it) },
        selected = state.calendar,
        onSelect = { calendar -> state = state.withCalendar(calendar); notice = null },
    ) { calendar ->
        if (calendar == state.calendar) {
            MonthView(
                state = state,
                reload = reload,
                onState = { state = it },
                onChanged = { message ->
                    notice = message to NoticeTone.SUCCESS
                    reload++
                },
            )
        }
    }
}

@Composable
private fun MonthView(state: CalendarViewState, reload: Int, onState: (CalendarViewState) -> Unit, onChanged: (String) -> Unit) {
    val today = todayIn(state.calendar, Date.now())
    var days by remember { mutableStateOf<Map<String, DayDto>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    LaunchedEffect(state, reload) {
        loading = true
        when (val result = AdminApp.api.calendarDays(state.calendar, state.toApiQuery(today))) {
            is ApiResult.Success -> {
                days = result.data.items.associateBy { it.day }
                error = null
            }
            is ApiResult.Error -> {
                days = emptyMap()
                error = if (result.code == "not_found") Strings.Calendar.NOT_SET_UP else generalMessage(result)
            }
        }
        loading = false
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "12px").toAttrs { attr("aria-busy", loading.toString()) }) {
        Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "flex-wrap" to "wrap").toAttrs()) {
            IconButton(Strings.Calendar.PREVIOUS_MONTH, onClick = { onState(state.previous()) }) { LucideChevronLeft() }
            H2(SectionTitleStyle.toModifier().css("min-width" to "150px", "text-align" to "center").toAttrs { attr("aria-live", "polite") }) {
                Text(Strings.Calendar.month(state.month))
            }
            IconButton(Strings.Calendar.NEXT_MONTH, onClick = { onState(state.next()) }) { LucideChevronRight() }
            val thisMonth = YearMonth(today.year, today.month)
            ActionButton(
                Strings.Calendar.THIS_MONTH,
                { onState(state.copy(month = thisMonth)) },
                ButtonKind.QUIET,
                enabled = state.month != thisMonth,
                modifier = Modifier.css("height" to "34px"),
            )
        }
        Notice(error, NoticeTone.ERROR)
        CalendarMonthGrid(state.month, today, days, onPick = { day, current -> picking = PickTarget(day, current) })
    }

    picking?.let { target ->
        PickDayDialog(
            calendar = state.calendar,
            day = target.day,
            current = target.current,
            onDismiss = { picking = null },
            onChanged = { message ->
                picking = null
                onChanged(message)
            },
        )
    }
}

/** Undismissed "a manual pick was replaced" notices, with dismiss; nothing is shown when there are none. */
@Composable
private fun Notices(reload: Int) {
    val scope = rememberCoroutineScope()
    var notices by remember { mutableStateOf<List<CalendarNoticeDto>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        (AdminApp.api.calendarNotices() as? ApiResult.Success)?.let { notices = it.data }
    }
    if (notices.isEmpty()) return

    Div(PanelStyle.toModifier().css("padding" to "14px 16px", "margin-bottom" to "16px", "border-left" to "4px solid ${Tokens.PRESENT}").toAttrs {
        attr("role", "region")
        attr("aria-label", Strings.Calendar.NOTICES_TITLE)
    }) {
        Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "margin-bottom" to "8px").toAttrs()) {
            LucideBellRing(Modifier.css("width" to "18px", "height" to "18px"))
            H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Calendar.NOTICES_TITLE) }
        }
        Ul(Modifier.css("margin" to "0", "padding" to "0", "list-style" to "none", "display" to "flex", "flex-direction" to "column", "gap" to "6px").toAttrs()) {
            notices.forEach { item ->
                Li(Modifier.css("display" to "flex", "align-items" to "center", "justify-content" to "space-between", "gap" to "12px", "flex-wrap" to "wrap").toAttrs()) {
                    Span { Text(Strings.Calendar.notice(item)) }
                    ActionButton(
                        Strings.Calendar.DISMISS,
                        {
                            busy = item.id
                            scope.launch {
                                if (AdminApp.api.dismissNotice(item.id) is ApiResult.Success) notices = notices - item
                                busy = null
                            }
                        },
                        ButtonKind.QUIET,
                        enabled = busy == null,
                        modifier = Modifier.css("height" to "30px", "padding" to "0 10px"),
                    )
                }
            }
        }
    }
}
