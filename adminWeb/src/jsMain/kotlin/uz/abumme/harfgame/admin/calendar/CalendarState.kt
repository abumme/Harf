package uz.abumme.harfgame.admin.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarParams
import uz.abumme.harfgame.data.admin.calendar.CalendarReasons
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.api.ApiResult

/** The `/calendar` month view: which calendar and month, kept in the page's query parameters. */
data class CalendarViewState(val calendar: String, val month: YearMonth) {
    fun withCalendar(calendar: String) = copy(calendar = calendar)
    fun next() = copy(month = month.plusMonth())
    fun previous() = copy(month = month.minusMonth())

    fun toRouteQuery(): String = queryString(PARAM_CALENDAR to calendar, PARAM_MONTH to month.toString())

    /** The admin API query of every day the month grid shows (including the neighbouring months' days). */
    fun toApiQuery(today: LocalDate): String {
        val weeks = monthGrid(month, today)
        return queryString(
            CalendarParams.FROM to weeks.first().first().date.toString(),
            CalendarParams.TO to weeks.last().last().date.toString(),
            CalendarParams.SIZE to CalendarParams.MAX_SIZE.toString(),
        )
    }

    companion object {
        const val PARAM_CALENDAR = "calendar"
        const val PARAM_MONTH = "month"

        /** The view a URL describes; an unknown calendar or month falls back to `en` and [today]'s month. */
        fun fromParams(params: Map<String, String>, today: LocalDate): CalendarViewState {
            val calendar = params[PARAM_CALENDAR]?.takeIf(DailyCalendars::isCalendar) ?: DailyCalendars.ALL.first()
            val month = params[PARAM_MONTH]?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth(today.year, today.month)
            return CalendarViewState(calendar, month)
        }
    }
}

/** The `/calendar/table` view: calendar, inclusive date range, source filter, repeats only and page. */
data class CalendarTableState(
    val calendar: String,
    /** `yyyy-mm-dd` from a date input, or empty (the server then starts today and ends 60 days ahead). */
    val from: String = "",
    val to: String = "",
    /** A [DaySource] name, or empty for any. */
    val source: String = "",
    val repeatsOnly: Boolean = false,
    val page: Int = 0,
) {
    fun withCalendar(calendar: String) = if (calendar == this.calendar) this else copy(calendar = calendar, page = 0)
    fun withFrom(date: String) = copy(from = date, page = 0)
    fun withTo(date: String) = copy(to = date, page = 0)
    fun withSource(source: String) = copy(source = source, page = 0)
    fun withRepeatsOnly(repeatsOnly: Boolean) = copy(repeatsOnly = repeatsOnly, page = 0)
    fun withPage(page: Int) = copy(page = maxOf(0, page))

    val hasFilters: Boolean get() = from.isNotEmpty() || to.isNotEmpty() || source.isNotEmpty() || repeatsOnly

    fun withoutFilters() = CalendarTableState(calendar)

    fun toRouteQuery(): String = queryString(
        PARAM_CALENDAR to calendar,
        PARAM_FROM to from.ifEmpty { null },
        PARAM_TO to to.ifEmpty { null },
        PARAM_SOURCE to source.ifEmpty { null },
        PARAM_REPEATS to "1".takeIf { repeatsOnly },
        PARAM_PAGE to (page + 1).toString().takeIf { page > 0 },
    )

    fun toApiQuery(size: Int): String = queryString(
        CalendarParams.FROM to from.ifEmpty { null },
        CalendarParams.TO to to.ifEmpty { null },
        CalendarParams.SOURCE to source.ifEmpty { null },
        CalendarParams.REPEATS_ONLY to "true".takeIf { repeatsOnly },
        CalendarParams.PAGE to page.toString(),
        CalendarParams.SIZE to size.toString(),
    )

    companion object {
        const val PARAM_CALENDAR = "calendar"
        const val PARAM_FROM = "from"
        const val PARAM_TO = "to"
        const val PARAM_SOURCE = "source"
        const val PARAM_REPEATS = "repeats"

        /** 1-based in the URL, like the page number the table shows. */
        const val PARAM_PAGE = "page"

        private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

        /** The view a URL describes; malformed values fall back to their defaults. */
        fun fromParams(params: Map<String, String>): CalendarTableState = CalendarTableState(
            calendar = params[PARAM_CALENDAR]?.takeIf(DailyCalendars::isCalendar) ?: DailyCalendars.ALL.first(),
            from = params[PARAM_FROM]?.takeIf { DATE.matches(it) }.orEmpty(),
            to = params[PARAM_TO]?.takeIf { DATE.matches(it) }.orEmpty(),
            source = params[PARAM_SOURCE]?.takeIf { value -> DaySource.entries.any { it.name == value } }.orEmpty(),
            repeatsOnly = params[PARAM_REPEATS] == "1",
            page = (params[PARAM_PAGE]?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1,
        )
    }
}

/** How a picker candidate has been used, in one line: never used, last used, or the day it is scheduled on. */
fun candidateUsage(candidate: CalendarCandidateDto): String = buildList {
    add(if (candidate.neverUsed) Strings.Calendar.NEVER_USED else Strings.Calendar.lastUsed(candidate.lastUsed))
    candidate.scheduledOn?.let { add(Strings.Calendar.scheduledOn(it.day, it.source)) }
}.joinToString(" · ")

/** The message for a refused pick or unpick: the days a word is used or picked on, a lock, or a general message. */
fun calendarErrorMessage(error: ApiResult.Error): String {
    val field = error.fieldError() ?: return generalMessage(error)
    CalendarReasons.parseConflict(field.reason)?.let { (kind, days) ->
        return if (kind == CalendarReasons.USED) Strings.Calendar.usedOn(days) else Strings.Calendar.pickedOn(days)
    }
    return Strings.Calendar.reason(field.field, field.reason)
}
