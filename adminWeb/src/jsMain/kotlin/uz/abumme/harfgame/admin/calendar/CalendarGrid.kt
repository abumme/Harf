package uz.abumme.harfgame.admin.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import kotlin.js.Date

/** Where a day stands against the lock: past days, today and tomorrow cannot change. */
enum class DayLock {
    PAST,
    TODAY,
    TOMORROW,

    /** The day after tomorrow or later: an ADMIN may pick its word. */
    OPEN;

    val locked: Boolean get() = this != OPEN
}

/** One cell of a month grid; [inMonth] is false for the leading and trailing days of neighbouring months. */
data class GridCell(val date: LocalDate, val inMonth: Boolean, val lock: DayLock)

/** The lock of [day] when it is [today] in the calendar's timezone. */
fun lockOf(day: LocalDate, today: LocalDate): DayLock {
    val offset = day.toEpochDays() - today.toEpochDays()
    return when {
        offset < 0 -> DayLock.PAST
        offset == 0L -> DayLock.TODAY
        offset < DailyCalendars.FIRST_OPEN_OFFSET -> DayLock.TOMORROW
        else -> DayLock.OPEN
    }
}

/**
 * The weeks (Monday first, as in Russian calendars) that cover [month]: whole weeks of seven cells, with the days of the
 * previous and next months that complete the first and last week.
 */
fun monthGrid(month: YearMonth, today: LocalDate): List<List<GridCell>> {
    val first = month.firstDay
    val start = first.minus(first.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber, DateTimeUnit.DAY)
    val last = month.lastDay
    val end = last.plus(DayOfWeek.SUNDAY.isoDayNumber - last.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    val cells = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= end }
        .map { GridCell(it, it.yearMonth == month, lockOf(it, today)) }
        .toList()
    return cells.chunked(7)
}

/**
 * Today in [calendar]'s timezone at [epochMillis]. The zone comes from the shared `PuzzleDays` table the app and the
 * server use; the date in that zone comes from the browser's own time zone data (`Intl`), which, unlike
 * kotlinx-datetime on Kotlin/JS, needs no bundled database. The server's `locked` flag stays the authority.
 */
fun todayIn(calendar: String, epochMillis: Double): LocalDate =
    dateInZone(PuzzleDays.zoneOf(DailyCalendars.wordLanguage(calendar)), epochMillis)

/** The date at [epochMillis] in the IANA [zone], from the browser's `Intl` time zone data. */
fun dateInZone(zone: String, epochMillis: Double): LocalDate {
    val options: dynamic = js("({})")
    options.timeZone = zone
    options.year = "numeric"
    options.month = "2-digit"
    options.day = "2-digit"
    val parts: Array<dynamic> = Intl.DateTimeFormat("en-US", options).formatToParts(Date(epochMillis))
    fun part(type: String): Int = parts.first { it.type == type }.value.unsafeCast<String>().toInt()
    return LocalDate(part("year"), part("month"), part("day"))
}

/** The browser's ECMAScript Internationalization API. */
private external object Intl {
    class DateTimeFormat(locales: String, options: dynamic) {
        fun formatToParts(date: Date): Array<dynamic>
    }
}
