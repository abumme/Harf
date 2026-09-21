package uz.abumme.harfgame.backend.admin.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsDaysTest {

    private fun at(zone: String, dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(ZoneId.of(zone)).toInstant()

    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    @Test
    fun aPuzzleDayClosesAtMidnightInItsLanguagesZone() {
        // English and Uzbek roll over in Tashkent, Kazakh in Almaty, Russian in Moscow.
        for ((lang, zone) in listOf("en" to "Asia/Tashkent", "uz-latn" to "Asia/Tashkent", "kk" to "Asia/Almaty", "ru" to "Europe/Moscow")) {
            assertEquals(day("2026-09-15"), AnalyticsDays.closedPuzzleDays(lang, at(zone, "2026-09-16T23:59:59")), "$lang before midnight")
            assertEquals(day("2026-09-16"), AnalyticsDays.closedPuzzleDays(lang, at(zone, "2026-09-17T00:00:01")), "$lang after midnight")
        }
    }

    @Test
    fun aCrossLanguageDayClosesOnlyOnceItEndedInEveryZone() {
        // 00:00:01 in Tashkent and Almaty is still 22:00 of the day before in Moscow.
        assertEquals(day("2026-09-15"), AnalyticsDays.closedGlobalPuzzleDay(at("Asia/Tashkent", "2026-09-17T00:00:01")))
        assertEquals(day("2026-09-15"), AnalyticsDays.closedGlobalPuzzleDay(at("Asia/Almaty", "2026-09-17T00:00:01")))
        assertEquals(day("2026-09-15"), AnalyticsDays.closedGlobalPuzzleDay(at("Europe/Moscow", "2026-09-16T23:59:59")))
        assertEquals(day("2026-09-16"), AnalyticsDays.closedGlobalPuzzleDay(at("Europe/Moscow", "2026-09-17T00:00:01")))
        assertEquals(at("Europe/Moscow", "2026-09-17T00:00:00"), AnalyticsDays.closeOfGlobalDay(day("2026-09-16")))
    }

    @Test
    fun eventDatesAreAsiaTashkentDates() {
        assertEquals(LocalDate.parse("2026-09-15"), AnalyticsDays.closedEventDates(at("Asia/Tashkent", "2026-09-16T23:59:59")))
        assertEquals(LocalDate.parse("2026-09-16"), AnalyticsDays.closedEventDates(at("Asia/Tashkent", "2026-09-17T00:00:01")))
        // 23:59:59 in Almaty is the same Tashkent date (both UTC+5); 23:59:59 in Moscow is already the next one.
        assertEquals(LocalDate.parse("2026-09-15"), AnalyticsDays.closedEventDates(at("Asia/Almaty", "2026-09-16T23:59:59")))
        assertEquals(LocalDate.parse("2026-09-16"), AnalyticsDays.closedEventDates(at("Europe/Moscow", "2026-09-16T23:59:59")))
        assertEquals(LocalDate.parse("2026-09-16"), AnalyticsDays.closedEventDates(at("Europe/Moscow", "2026-09-17T00:00:01")))
    }

    @Test
    fun aDayIsFinalSevenDaysAfterItClosed() {
        val close = AnalyticsDays.closeOfPuzzleDay("ru", day("2026-09-01"))
        assertEquals(at("Europe/Moscow", "2026-09-02T00:00:00"), close)
        assertFalse(AnalyticsDays.isFinal(close, at("Europe/Moscow", "2026-09-08T23:59:59")))
        assertTrue(AnalyticsDays.isFinal(close, at("Europe/Moscow", "2026-09-09T00:00:00")))
    }
}
