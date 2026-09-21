package uz.abumme.harfgame.data

import kotlinx.datetime.LocalDate
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PuzzleDaysTest {

    private fun day(iso: String) = LocalDate.parse(iso).toEpochDays().toLong()

    @Test
    fun eachLanguageHasItsZone() {
        assertEquals("Asia/Tashkent", PuzzleDays.zoneOf("en"))
        assertEquals("Asia/Tashkent", PuzzleDays.zoneOf("uz-latn"))
        assertEquals("Asia/Tashkent", PuzzleDays.zoneOf("uz-cyrl"))
        assertEquals("Asia/Almaty", PuzzleDays.zoneOf("kk"))
        assertEquals("Europe/Moscow", PuzzleDays.zoneOf("ru"))
        assertEquals(PuzzleDays.DEFAULT_ZONE, PuzzleDays.zoneOf("xx"))
    }

    @Test
    fun theDayTurnsAtLocalMidnight() {
        // Moscow is UTC+3; Tashkent and Almaty are UTC+5.
        val cases = listOf(
            Triple("ru", "2026-09-17T20:59:59Z", "2026-09-17"),
            Triple("ru", "2026-09-17T21:00:01Z", "2026-09-18"),
            Triple("en", "2026-09-17T18:59:59Z", "2026-09-17"),
            Triple("en", "2026-09-17T19:00:01Z", "2026-09-18"),
            Triple("kk", "2026-09-17T18:59:59Z", "2026-09-17"),
            Triple("kk", "2026-09-17T19:00:01Z", "2026-09-18"),
            Triple("uz-cyrl", "2026-09-17T19:00:01Z", "2026-09-18"),
        )
        for ((lang, instant, expected) in cases) {
            assertEquals(day(expected), PuzzleDays.epochDay(lang, Instant.parse(instant)), "$lang at $instant")
        }
    }

    @Test
    fun localTwentyThreeFiftyNineIsStillTodayAndMidnightIsTomorrow() {
        // 23:59 and 00:00 local time in each zone, written with the zone's offset.
        val zones = listOf(
            "en" to "+05:00", // Asia/Tashkent
            "kk" to "+05:00", // Asia/Almaty
            "ru" to "+03:00", // Europe/Moscow
        )
        for ((lang, offset) in zones) {
            val lastMinute = Instant.parse("2026-12-31T23:59:00$offset")
            val midnight = Instant.parse("2027-01-01T00:00:00$offset")
            assertEquals(day("2026-12-31"), PuzzleDays.epochDay(lang, lastMinute), "$lang at 23:59")
            assertEquals(day("2027-01-01"), PuzzleDays.epochDay(lang, midnight), "$lang at 00:00")
            assertEquals(1L, PuzzleDays.epochDay(lang, midnight) - PuzzleDays.epochDay(lang, lastMinute))
        }
    }
}
