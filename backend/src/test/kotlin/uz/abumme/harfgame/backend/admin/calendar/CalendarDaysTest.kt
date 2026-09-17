package uz.abumme.harfgame.backend.admin.calendar

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarDaysTest {

    /** 23:59:59 and 00:00:01 local time on 2026-09-17/18, per calendar zone (Moscow UTC+3, Almaty and Tashkent UTC+5). */
    private val midnights = mapOf(
        "ru" to ("2026-09-17T20:59:59Z" to "2026-09-17T21:00:01Z"),
        "kk" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
        "en" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
        "uz" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
    )

    @Test
    fun theDayAfterTomorrowIsPickableBeforeMidnightAndLockedAfter() {
        val target = LocalDate.parse("2026-09-19")
        for ((calendar, instants) in midnights) {
            val (before, after) = instants
            val todayBefore = CalendarDays.today(calendar, Instant.parse(before))
            val todayAfter = CalendarDays.today(calendar, Instant.parse(after))
            assertEquals(LocalDate.parse("2026-09-17"), todayBefore, calendar)
            assertEquals(LocalDate.parse("2026-09-18"), todayAfter, calendar)

            assertFalse(CalendarDays.isLocked(target, todayBefore), "$calendar: 2026-09-19 is the day after tomorrow at 23:59:59")
            assertTrue(CalendarDays.isLocked(target, todayAfter), "$calendar: 2026-09-19 is tomorrow at 00:00:01")
            assertEquals(target, CalendarDays.firstOpen(todayBefore))
        }
    }

    @Test
    fun calendarsTurnAtTheirOwnMidnight() {
        // 20:30 UTC is 23:30 in Moscow but already 01:30 of the next day in Almaty and Tashkent.
        val at = Instant.parse("2026-09-17T20:30:00Z")
        assertEquals(LocalDate.parse("2026-09-17"), CalendarDays.today("ru", at))
        assertEquals(LocalDate.parse("2026-09-18"), CalendarDays.today("kk", at))
        assertEquals(LocalDate.parse("2026-09-18"), CalendarDays.today("uz", at))
    }

    @Test
    fun picksReachAYearAhead() {
        val today = LocalDate.parse("2026-09-17")
        assertFalse(CalendarDays.isTooFar(today.plusDays(365), today))
        assertTrue(CalendarDays.isTooFar(today.plusDays(366), today))
        assertTrue(CalendarDays.isLocked(today.minusDays(3), today))
        assertTrue(CalendarDays.isLocked(today.plusDays(1), today))
        assertEquals(today.plusDays(60), CalendarDays.horizonEnd(today))
    }
}
