package uz.abumme.harfgame.tools.wordlists

import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.wordpack.CalendarSnapshotDto
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalendarSnapshotRefreshTest {
    private val anchor = WordPackSchedule.ANCHOR_EPOCH_DAY

    private val published = mapOf(
        "en" to listOf("crane", "bread"),
        "ru" to listOf("книга", "слово"),
        "kk" to listOf("кітап", "қалам"),
        "uz-latn" to listOf("kitob", "shahar"),
        "uz-cyrl" to listOf("китоб", "шаҳар"),
    )

    private fun packJson(lang: String, words: List<String> = published.getValue(lang), schedule: List<String> = words + words) =
        Json.encodeToString(WordPackDto(lang, "12", anchor + 259, anchor, words, words + "extra", schedule))

    @Test
    fun everyLanguageGetsItsPublishedAnswersAndScheduleWithoutGuesses() {
        val files = CalendarSnapshotRefresh.snapshots(fetch = { packJson(it) }, guesses = { emptyList() })

        assertEquals(CalendarSnapshotRefresh.languages.toSet(), files.keys)
        val uz = Json.decodeFromString<CalendarSnapshotDto>(files.getValue("uz-cyrl"))
        assertEquals(CalendarSnapshotDto("uz-cyrl", "12", anchor, listOf("китоб", "шаҳар"), listOf("китоб", "шаҳар", "китоб", "шаҳар")), uz)
        assertTrue(files.values.none { "extra" in it }, "guesses stay in the bundled dictionaries")
        assertEquals("files/en_calendar.json", CalendarSnapshotDto.resourcePath("en"))
    }

    @Test
    fun aPackTheAppWouldRefuseWritesNothing() {
        val error = assertFailsWith<IllegalStateException> {
            CalendarSnapshotRefresh.snapshots(
                fetch = { if (it == "kk") packJson("kk", listOf("toolongforkk")) else packJson(it) },
                guesses = { emptyList() },
            )
        }
        assertTrue(error.message!!.startsWith("kk:"), error.message)
        assertFailsWith<IllegalStateException> { CalendarSnapshotRefresh.snapshots(fetch = { packJson("en") }, guesses = { emptyList() }) }
        assertFailsWith<IllegalStateException> { CalendarSnapshotRefresh.snapshots(fetch = { "<html>" }, guesses = { emptyList() }) }
    }

    @Test
    fun uzbekScriptsMustDescribeTheSameDays() {
        assertFailsWith<IllegalStateException> {
            CalendarSnapshotRefresh.snapshots(
                fetch = { if (it == "uz-cyrl") packJson(it, schedule = listOf("китоб")) else packJson(it) },
                guesses = { emptyList() },
            )
        }
    }
}
