package uz.abumme.harfgame.backend.admin.calendar

import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.words.PackIntegrityException
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.words.WordReasons
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate

/** How calendar and answer-pool requests run: checks, pack locks, one `now`, and integrity refusals as 422. */
internal class CalendarTransactions(private val catalog: WordCatalogService) {

    /**
     * Runs [block] at READ COMMITTED under [calendar]'s pack locks with `now` and the calendar's `today` taken once. An
     * unknown or not yet initialized calendar answers 404; a pack the app would refuse rolls everything back and answers
     * `422 pack: pack_integrity`.
     */
    suspend fun <T> write(calendar: String, block: (now: Instant, today: LocalDate) -> T): T {
        requireCalendarId(calendar)
        return try {
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                if (!lockCalendar(calendar)) throw AdminApiException.notFound("No word pack for calendar $calendar")
                requireInitialized(calendar)
                val now = catalog.clock.instant()
                block(now, CalendarDays.today(calendar, now))
            }
        } catch (e: PackIntegrityException) {
            System.err.println(e.message)
            throw AdminApiException.validation("pack", WordReasons.PACK_INTEGRITY)
        }
    }

    /** Locks the calendar's pack rows in the fixed order; false when a pack is missing. */
    fun lockCalendar(calendar: String): Boolean =
        DailyCalendars.packLanguages(calendar).all { catalog.publisher.lock(it) != null }

    fun requireCalendarId(calendar: String) {
        if (!DailyCalendars.isCalendar(calendar)) throw AdminApiException.notFound("No calendar $calendar")
    }

    /** Inside a transaction: a known calendar that has been initialized. */
    fun requireCalendar(calendar: String) {
        requireCalendarId(calendar)
        requireInitialized(calendar)
    }

    private fun requireInitialized(calendar: String) {
        if (catalog.calendars.initializedOn(calendar) == null) throw AdminApiException.notFound("Calendar $calendar is not set up yet")
    }
}
