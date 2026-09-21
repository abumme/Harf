package uz.abumme.harfgame.backend.admin.calendar

import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import java.time.Instant
import java.time.LocalDate

/**
 * Keeps every calendar filled as days pass, with no staff action. Called at startup and then every minute; a calendar is
 * reconciled only when its `today` changed since the last tick or its horizon is short, so a quiet minute touches
 * nothing, and a reconcile that changes nothing publishes nothing. In steady state each pack's version advances once a
 * day, when its horizon grows by one day.
 */
class DailyCalendarScheduler(private val calendars: CalendarService) {
    private val lastToday = HashMap<String, LocalDate>()

    /** Refreshes the calendars that are due at [now]; returns the ones it reconciled. */
    suspend fun tick(now: Instant): List<String> {
        val refreshed = ArrayList<String>()
        for (calendar in DailyCalendars.ALL) {
            val today = CalendarDays.today(calendar, now)
            if (calendars.refresh(calendar, now, dayChanged = lastToday[calendar] != today)) refreshed += calendar
            lastToday[calendar] = today
        }
        return refreshed
    }
}
