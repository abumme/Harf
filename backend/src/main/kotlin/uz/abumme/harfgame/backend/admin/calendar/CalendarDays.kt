package uz.abumme.harfgame.backend.admin.calendar

import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import java.time.Instant
import java.time.LocalDate
import kotlin.time.ExperimentalTime

/**
 * A calendar's days, counted in its language's timezone with the app's own [PuzzleDays], from one instant. Every check of
 * one request or tick uses the `today` computed once from that instant, so a pick at 23:59:59 and one at 00:00:01
 * resolve deterministically.
 */
object CalendarDays {

    /** Today in [calendar]'s timezone at [now]. */
    @OptIn(ExperimentalTime::class)
    fun today(calendar: String, now: Instant): LocalDate {
        val instant = kotlin.time.Instant.fromEpochSeconds(now.epochSecond, now.nano)
        return LocalDate.ofEpochDay(PuzzleDays.epochDay(DailyCalendars.wordLanguage(calendar), instant))
    }

    /** The day after tomorrow: the earliest day whose word may still change. */
    fun firstOpen(today: LocalDate): LocalDate = today.plusDays(DailyCalendars.FIRST_OPEN_OFFSET.toLong())

    /** The last day every calendar keeps filled. */
    fun horizonEnd(today: LocalDate): LocalDate = today.plusDays(DailyCalendars.HORIZON_DAYS.toLong())

    /** Past days, today and tomorrow. */
    fun isLocked(day: LocalDate, today: LocalDate): Boolean = day < firstOpen(today)

    /** Further ahead than an ADMIN may pick. */
    fun isTooFar(day: LocalDate, today: LocalDate): Boolean = day > today.plusDays(DailyCalendars.MAX_PICK_DAYS.toLong())
}
