package uz.abumme.harfgame.backend.admin.calendar

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.DailyWordsTable
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyCalendarSchedulerTest {
    private val clock = MutableClock(CALENDAR_NOW)

    @BeforeTest
    fun setup() = resetCalendarData()

    @Test
    fun aNewDayExtendsItsCalendarOnceAndAQuietMinuteTouchesNothing() = runBlocking {
        val catalog = calendarCatalog(clock)
        migrateCalendars(catalog)
        val scheduler = DailyCalendarScheduler(CalendarService(catalog))
        val versions = { listOf("en", "kk", "ru", "uz-cyrl", "uz-latn").associateWith { pack(it).version } }
        val start = versions()

        // Startup tick: every calendar is checked, nothing is due, nothing is published.
        scheduler.tick(clock.now)
        assertEquals(start, versions())
        clock.now = Instant.parse("2026-09-17T18:59:00Z")
        assertEquals(emptyList(), scheduler.tick(clock.now))
        assertEquals(start, versions())

        // 19:30 UTC: past midnight in Tashkent and Almaty (UTC+5), still 22:30 in Moscow.
        clock.now = Instant.parse("2026-09-17T19:30:00Z")
        val enSchedule = pack("en").schedule.size
        assertEquals(listOf("en", "kk", "uz"), scheduler.tick(clock.now))
        assertEquals("3", pack("en").version)
        assertEquals(enSchedule + 1, pack("en").schedule.size, "the horizon grew by one day")
        assertEquals(TODAY.plusDays(61), calendarRows("en").last()[DailyWordsTable.day])
        assertEquals(TODAY.plusDays(3).toEpochDay(), pack("en").effectiveFrom)
        assertEquals("3", pack("uz-latn").version)
        assertEquals("3", pack("uz-cyrl").version)
        assertEquals(start.getValue("ru"), pack("ru").version)

        // The same day again: nothing to do.
        clock.now = Instant.parse("2026-09-17T19:31:00Z")
        assertEquals(emptyList(), scheduler.tick(clock.now))
        assertEquals("3", pack("en").version)

        // Moscow's midnight.
        clock.now = Instant.parse("2026-09-17T21:00:30Z")
        assertEquals(listOf("ru"), scheduler.tick(clock.now))
        assertEquals("3", pack("ru").version)
        assertEquals("3", pack("en").version)
    }

    @Test
    fun aShortHorizonIsFilledEvenWithoutANewDay() = runBlocking {
        val catalog = calendarCatalog(clock)
        migrateCalendars(catalog)
        val scheduler = DailyCalendarScheduler(CalendarService(catalog))
        scheduler.tick(clock.now)
        // Lose the last ten days (as if the horizon had never been filled that far).
        transaction(DatabaseFactory.init()) {
            DailyWordsTable.deleteWhere { DailyWordsTable.day greaterEq TODAY.plusDays(51) }
        }
        clock.advance(java.time.Duration.ofMinutes(1))
        assertEquals(listOf("en", "ru", "kk", "uz"), scheduler.tick(clock.now))
        assertEquals(TODAY.plusDays(60), calendarRows("ru").last()[DailyWordsTable.day])
    }
}
