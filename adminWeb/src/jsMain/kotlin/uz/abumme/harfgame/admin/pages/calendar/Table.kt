package uz.abumme.harfgame.admin.pages.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.icons.lucide.LucideCalendarDays
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLock
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.builders.InputAttrsScope
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.calendar.CalendarTableState
import uz.abumme.harfgame.admin.calendar.CalendarViewState
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.DateField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.QuietButtonStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SelectField
import uz.abumme.harfgame.admin.components.StateTabs
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.pages.AuditFiltersStyle
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.calendar.CalendarParams
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.api.ApiResult

/**
 * `/calendar/table`: a calendar's days as a server-paged table, filtered by date range, how the word was picked, and
 * repeats only. The view lives in the query string; the month view is one link away.
 */
@Page
@Composable
fun CalendarTablePage() {
    RequireSession(PageAccess.CALENDAR) { me ->
        AdminShell(me, NavSection.CALENDAR) { CalendarTable() }
    }
}

@Composable
private fun CalendarTable() {
    val ctx = rememberPageContext()
    var state by remember { mutableStateOf(CalendarTableState.fromParams(ctx.route.params)) }

    LaunchedEffect(state) { ctx.router.navigateTo(Routes.CALENDAR_TABLE + state.toRouteQuery(), UpdateHistoryMode.REPLACE) }

    PageHeader(Strings.Calendar.TITLE, Strings.Calendar.TABLE_HINT) {
        Anchor(Routes.CALENDAR + queryString(CalendarViewState.PARAM_CALENDAR to state.calendar), QuietButtonStyle.toModifier().toAttrs()) {
            LucideCalendarDays()
            Text(Strings.Calendar.TAB_MONTH)
        }
    }
    StateTabs(
        options = DailyCalendars.ALL.map { it to Strings.Calendar.calendar(it) },
        selected = state.calendar,
        onSelect = { calendar -> state = state.withCalendar(calendar) },
    ) { calendar ->
        if (calendar == state.calendar) DayTable(state, onState = { update -> state = update(state) })
    }
}

@Composable
private fun DayTable(state: CalendarTableState, onState: ((CalendarTableState) -> CalendarTableState) -> Unit) {
    var days by remember { mutableStateOf<List<DayDto>>(emptyList()) }
    var paging by remember { mutableStateOf(PagingState(size = CalendarParams.DEFAULT_SIZE)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        loading = true
        when (val result = AdminApp.api.calendarDays(state.calendar, state.toApiQuery(CalendarParams.DEFAULT_SIZE))) {
            is ApiResult.Success -> {
                days = result.data.items
                paging = PagingState(page = state.page, size = CalendarParams.DEFAULT_SIZE).withTotal(result.data.total)
                error = null
                if (paging.page != state.page) onState { it.withPage(paging.page) }
            }
            is ApiResult.Error -> {
                days = emptyList()
                error = if (result.code == "not_found") Strings.Calendar.NOT_SET_UP else generalMessage(result)
            }
        }
        loading = false
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        Div(PanelStyle.toModifier().then(AuditFiltersStyle.toModifier()).toAttrs { attr("role", "search") }) {
            DateField("calendar-from", Strings.Calendar.FILTER_FROM, state.from, { date -> onState { it.withFrom(date) } })
            DateField("calendar-to", Strings.Calendar.FILTER_TO, state.to, { date -> onState { it.withTo(date) } })
            SelectField(
                "calendar-source",
                Strings.Calendar.FILTER_SOURCE,
                state.source,
                listOf("" to Strings.Calendar.ANY) + DaySource.entries.map { it.name to Strings.Calendar.sourceFilter(it) },
                { source -> onState { it.withSource(source) } },
            )
            Label(forId = "calendar-repeats", attrs = Modifier.css(
                "display" to "flex", "align-items" to "center", "gap" to "8px", "height" to "40px", "font-weight" to "600", "font-size" to "14px",
            ).toAttrs()) {
                Input(InputType.Checkbox, attrs = Modifier.css("accent-color" to Tokens.CORRECT, "margin" to "0").toAttrs<InputAttrsScope<Boolean>> {
                    id("calendar-repeats")
                    checked(state.repeatsOnly)
                    onChange { event -> onState { it.withRepeatsOnly(event.value) } }
                })
                Text(Strings.Calendar.FILTER_REPEATS)
            }
            if (state.hasFilters) {
                Div { ActionButton(Strings.Calendar.RESET_FILTERS, onClick = { onState { it.withoutFilters() } }) }
            }
        }
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = "${Strings.Calendar.TITLE} · ${Strings.Calendar.calendar(state.calendar)}",
            columns = dayColumns(),
            rows = days,
            emptyText = if (error != null) "" else Strings.Calendar.TABLE_EMPTY,
            loading = loading,
            paging = paging,
            onPageChange = { target -> onState { it.withPage(target.page) } },
        )
    }
}

private fun dayColumns(): List<TableColumn<DayDto>> = listOf(
    TableColumn(Strings.Calendar.COLUMN_DAY) { day ->
        Span(Modifier.css("display" to "inline-flex", "align-items" to "center", "gap" to "6px", "white-space" to "nowrap").toAttrs()) {
            Text(Strings.Calendar.shortDay(day.day))
            if (day.locked) {
                Span(Modifier.css("display" to "inline-flex", "color" to Tokens.MUTED).toAttrs { attr("title", Strings.Calendar.LOCKED) }) {
                    LucideLock(Modifier.css("width" to "13px", "height" to "13px"))
                }
            }
        }
    },
    TableColumn(Strings.Calendar.COLUMN_WORD) { day ->
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px").toAttrs()) {
            Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(day.text) }
            day.textCyrl?.let { Span(MutedTextStyle.toModifier().toAttrs()) { Text(it) } }
        }
    },
    TableColumn(Strings.Calendar.COLUMN_SOURCE) { day ->
        Badge(Strings.Calendar.source(day.source), if (day.source == DaySource.MANUAL) TileTone.CORRECT else TileTone.OPEN)
    },
    TableColumn(Strings.Calendar.COLUMN_REPEAT) { day ->
        if (day.isRepeat) Badge(Strings.Calendar.repeatSince(day.lastUsed), TileTone.PRESENT)
        else Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Common.NOT_SET) }
    },
    TableColumn(Strings.Calendar.COLUMN_PICKED_BY) { day ->
        Text(day.pickedBy?.let { it.displayName ?: it.username } ?: Strings.Common.NOT_SET)
    },
)
