package uz.abumme.harfgame.backend.admin.calendar

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.ScheduledOnDto
import java.time.LocalDate

/**
 * How a calendar has used its words, read inside a transaction from `daily_words` at one `today`: what the calendar
 * views, the picker and the answer pool show about a word. Keys are normalized texts ([CalendarReconciler.key]).
 */
class CalendarUsage private constructor(
    /** All stored days, ascending. */
    val rows: List<ResultRow>,
    val today: LocalDate,
    val historyStart: LocalDate,
    private val keys: Map<LocalDate, String>,
) {
    val firstOpen: LocalDate = CalendarDays.firstOpen(today)

    /** Counted days (history start through tomorrow) per word, ascending. */
    val usedDays: Map<String, List<LocalDate>> = rows.asSequence()
        .map { it[DailyWordsTable.day] }
        .filter { it >= historyStart && it < firstOpen }
        .groupBy({ keys.getValue(it) }, { it })

    /** Future days (from the day after tomorrow) per word, ascending, with how each was picked. */
    val scheduledDays: Map<String, List<ScheduledOnDto>> = rows.asSequence()
        .filter { it[DailyWordsTable.day] >= firstOpen }
        .groupBy({ keys.getValue(it[DailyWordsTable.day]) }, { ScheduledOnDto(it[DailyWordsTable.day].toString(), DaySource.valueOf(it[DailyWordsTable.daySource])) })

    /** Words manually picked for a future day. */
    val manualKeys: Set<String> = rows.asSequence()
        .filter { it[DailyWordsTable.day] >= firstOpen && it[DailyWordsTable.daySource] == DaySource.MANUAL.name }
        .mapTo(HashSet()) { keys.getValue(it[DailyWordsTable.day]) }

    fun key(day: LocalDate): String? = keys[day]

    fun lastUsed(key: String): LocalDate? = usedDays[key]?.lastOrNull()

    /** The next future day of [key], preferring one other than [except]. */
    fun scheduledOn(key: String, except: LocalDate? = null): ScheduledOnDto? {
        val days = scheduledDays[key] ?: return null
        return days.firstOrNull { it.day != except?.toString() } ?: days.firstOrNull()
    }

    /** Never the word of a counted day through tomorrow, and not manually picked for a future day. */
    fun isUnused(key: String): Boolean = key !in usedDays && key !in manualKeys

    /** For each repeat day, the previous counted use of its word. */
    fun previousUses(): Map<LocalDate, LocalDate> {
        val last = HashMap<String, LocalDate>()
        val previous = HashMap<LocalDate, LocalDate>()
        for (row in rows) {
            val day = row[DailyWordsTable.day]
            val key = keys.getValue(day)
            if (row[DailyWordsTable.isRepeat]) last[key]?.let { previous[day] = it }
            if (day >= historyStart) last[key] = day
        }
        return previous
    }

    companion object {
        fun load(calendars: CalendarReconciler, calendar: String, today: LocalDate): CalendarUsage {
            val rows = DailyWordsTable.selectAll().where { DailyWordsTable.calendar eq calendar }
                .orderBy(DailyWordsTable.day).toList()
            val keys = rows.associate { it[DailyWordsTable.day] to calendars.key(calendar, it[DailyWordsTable.text]) }
            val historyStart = calendars.historyStart(calendar) ?: today
            return CalendarUsage(rows, today, historyStart, keys)
        }
    }
}
