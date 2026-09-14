package uz.abumme.harfgame.backend.review

import uz.abumme.harfgame.backend.service.DailySummary
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyReportRenderTest {

    private val sep14 = LocalDate.of(2026, 9, 14)

    @Test
    fun reportDayIsTheTashkentDayThatJustEnded() {
        // Asia/Tashkent is UTC+5: 19:00Z is local midnight.
        assertEquals(LocalDate.of(2026, 9, 13), reportDay(Instant.parse("2026-09-14T18:59:59Z"))) // 23:59:59 on Sep 14
        assertEquals(sep14, reportDay(Instant.parse("2026-09-14T19:00:01Z"))) // 00:00:01 on Sep 15
        assertEquals(sep14, reportDay(Instant.parse("2026-09-15T18:59:59Z"))) // 23:59:59 on Sep 15
    }

    @Test
    fun reportWindowIsTheHalfOpenTashkentDay() {
        assertEquals(
            Instant.parse("2026-09-13T19:00:00Z") to Instant.parse("2026-09-14T19:00:00Z"),
            reportWindow(sep14),
        )
    }

    @Test
    fun quietDayHasNoReport() {
        assertNull(renderReport("ru", sep14, DailySummary(emptyList(), emptyList(), emptyList(), pending = 0)))
    }

    @Test
    fun reportListsEachGroupWithItsCountAndThePendingCount() {
        val summary = DailySummary(
            autoAccepted = listOf("книги", "поезд", "собак"),
            editorAccepted = listOf("батон"),
            rejected = listOf("бырка", "кряка"),
            pending = 4,
        )
        assertEquals(
            "📊 Отчёт за 14.09 · ru\n\n" +
                "🤖 Автопринято (3): книги, поезд, собак\n" +
                "✅ Принято редакторами (1): батон\n" +
                "❌ Отклонено (2): бырка, кряка\n" +
                "⏳ Ждут решения: 4",
            renderReport("ru", sep14, summary),
        )
    }

    @Test
    fun backlogAloneStillReports() {
        assertEquals(
            "📊 Отчёт за 14.09 · en\n\n" +
                "🤖 Автопринято (0): —\n" +
                "✅ Принято редакторами (0): —\n" +
                "❌ Отклонено (0): —\n" +
                "⏳ Ждут решения: 3",
            renderReport("en", sep14, DailySummary(emptyList(), emptyList(), emptyList(), pending = 3)),
        )
    }

    @Test
    fun longGroupsAreCutToFitOneTelegramMessageAndCountTheRest() {
        // The longest words validation allows (24 characters), 300 per group.
        val words = (1..300).map { "ж".repeat(20) + it.toString().padStart(4, '0') }
        val text = renderReport("ru", sep14, DailySummary(words, words, words, pending = 0))!!

        assertTrue(text.length <= 4096, "length=${text.length}")
        val groupLines = text.lines().filter { "(300)" in it }
        assertEquals(3, groupLines.size)
        for (line in groupLines) {
            val shown = line.substringAfter("): ").substringBefore(" …").split(", ")
            val omitted = line.substringAfter("…и ещё ").toInt()
            assertTrue(shown.size in 1 until 300, line)
            assertEquals(300, shown.size + omitted, line)
            assertEquals(words.take(shown.size), shown) // earliest decisions are the ones shown
        }
    }
}
