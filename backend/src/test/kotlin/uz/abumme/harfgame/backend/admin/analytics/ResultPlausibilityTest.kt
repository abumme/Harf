package uz.abumme.harfgame.backend.admin.analytics

import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultPlausibilityTest {
    private val languages = setOf("en", "kk", "ru", "uz-cyrl", "uz-latn")
    private val moscow = ZoneId.of("Europe/Moscow")
    private val sept16 = LocalDate.parse("2026-09-16").toEpochDay()

    private fun moscow(dateTime: String) = LocalDateTime.parse(dateTime).atZone(moscow).toInstant()

    @Test
    fun aValidRecordIsKept() {
        assertTrue(ResultPlausibility.isPlausible(ResultRecordDto("ru", sept16, won = true, attempts = 1), languages, moscow("2026-09-16T12:00:00")))
        assertTrue(ResultPlausibility.isPlausible(ResultRecordDto("ru", sept16 - 3, won = false, attempts = 6), languages, moscow("2026-09-16T12:00:00")))
    }

    @Test
    fun attemptsOutsideOneToSixAreDropped() {
        val now = moscow("2026-09-16T12:00:00")
        assertFalse(ResultPlausibility.isPlausible(ResultRecordDto("ru", sept16, won = true, attempts = 0), languages, now))
        assertFalse(ResultPlausibility.isPlausible(ResultRecordDto("ru", sept16, won = false, attempts = 7), languages, now))
    }

    @Test
    fun aLanguageWithoutAPackIsDropped() {
        assertFalse(ResultPlausibility.isPlausible(ResultRecordDto("uz", sept16, won = true, attempts = 3), languages, moscow("2026-09-16T12:00:00")))
    }

    @Test
    fun tomorrowsPuzzleDayIsDroppedUntilMidnightInTheLanguagesZone() {
        val tomorrow = ResultRecordDto("ru", sept16 + 1, won = true, attempts = 3)
        assertFalse(ResultPlausibility.isPlausible(tomorrow, languages, moscow("2026-09-16T23:59:00")))
        assertTrue(ResultPlausibility.isPlausible(tomorrow, languages, moscow("2026-09-17T00:01:00")))
    }

    @Test
    fun aSnapshotKeepsOneRecordPerLanguageAndDayTheFirstOneWins() {
        val now = moscow("2026-09-16T12:00:00")
        val records = listOf(
            ResultRecordDto("ru", sept16, won = true, attempts = 2),
            ResultRecordDto("ru", sept16, won = false, attempts = 6),
            ResultRecordDto("en", sept16, won = true, attempts = 7),
            ResultRecordDto("kk", sept16 - 1, won = true, attempts = 4),
        )
        assertEquals(listOf(records[0], records[3]), ResultPlausibility.plausible(records, languages, now))
    }
}
