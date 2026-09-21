package uz.abumme.harfgame.backend.admin.calendar

import uz.abumme.harfgame.backend.admin.calendar.CalendarEngine.AutoPick
import uz.abumme.harfgame.backend.admin.calendar.CalendarEngine.Candidate
import uz.abumme.harfgame.backend.admin.calendar.CalendarEngine.ManualPick
import uz.abumme.harfgame.backend.admin.calendar.CalendarEngine.Plan
import uz.abumme.harfgame.backend.admin.calendar.CalendarEngine.Source
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarEngineTest {

    private val today = LocalDate.parse("2026-09-17")
    private val tomorrow = today.plusDays(1)
    private val firstOpen = today.plusDays(2)
    private val horizonEnd = today.plusDays(60)

    private fun words(vararg texts: String) = texts.map { Candidate(id = "id-$it", text = it) }
    private fun pool(size: Int) = (1..size).map { Candidate("id-w$it", "w$it") }

    /** Two frozen days (today and tomorrow) with words outside [pool]. */
    private val quietHistory = mapOf(today to "legacy1", tomorrow to "legacy2")

    private fun plan(
        eligible: Collection<Candidate>,
        frozen: Map<LocalDate, String> = quietHistory,
        manual: Map<LocalDate, ManualPick> = emptyMap(),
        previousAuto: Map<LocalDate, AutoPick> = emptyMap(),
        historyStart: LocalDate = LocalDate.parse("2026-01-01"),
        seed: Int = 1,
    ): Plan = CalendarEngine.plan(today, historyStart, frozen, manual, previousAuto, eligible, Random(seed))

    /** A stored calendar after [plan]: its automatic days as the next run's previous picks. */
    private fun Plan.autoPicks(): Map<LocalDate, AutoPick> =
        days.filterValues { it.source == Source.AUTO }.mapValues { AutoPick(it.value.candidate.text, it.value.isRepeat) }

    private fun Plan.texts(): Map<LocalDate, String> = days.mapValues { it.value.candidate.text }

    @Test
    fun everyUnlockedDayUpToTheHorizonGetsADistinctNeverUsedWord() {
        val result = plan(pool(100))

        assertEquals((2L..60L).map { today.plusDays(it) }, result.days.keys.toList())
        val texts = result.days.values.map { it.candidate.text }
        assertEquals(texts.size, texts.toSet().size, "no word on two days")
        assertTrue(result.days.values.all { it.source == Source.AUTO && !it.isRepeat && it.lastUse == null })
        assertTrue(result.replacedManual.isEmpty())
    }

    @Test
    fun frozenDaysAreNeverInThePlan() {
        val frozen = (0L..40L).associate { today.minusDays(it) to "old$it" } + (tomorrow to "legacy2")
        val result = plan(pool(80), frozen = frozen)
        assertTrue(result.days.keys.all { it >= firstOpen })
        // Even a manual pick sent for a locked day is ignored.
        val locked = plan(pool(80), manual = mapOf(tomorrow to ManualPick("id-w1", "w1", wordActive = true)))
        assertFalse(tomorrow in locked.days)
    }

    @Test
    fun anExhaustedPoolReusesTheLeastRecentlyUsedWordAndMarksTheRepeat() {
        val frozen = mapOf(
            today.minusDays(2) to "alpha",
            today.minusDays(1) to "bravo",
            today to "charlie",
            tomorrow to "bravo",
        )
        val result = plan(words("alpha", "bravo", "charlie"), frozen = frozen)

        val first = result.days.getValue(firstOpen)
        assertEquals("alpha", first.candidate.text, "alpha was last used longest ago")
        assertTrue(first.isRepeat)
        assertEquals(today.minusDays(2), first.lastUse)

        val second = result.days.getValue(firstOpen.plusDays(1))
        assertEquals("charlie", second.candidate.text)
        assertEquals(today, second.lastUse)
        assertTrue(result.days.values.all { it.isRepeat })
        // Consecutive days never share a word while the pool has more than one.
        result.days.values.map { it.candidate.text }.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertNotEquals("bravo", first.candidate.text, "tomorrow's word is not repeated the next day")
    }

    @Test
    fun aOneWordPoolRepeatsItEveryDay() {
        val result = plan(words("solo"), frozen = mapOf(tomorrow to "solo"))
        assertTrue(result.days.values.all { it.candidate.text == "solo" && it.isRepeat })
        assertEquals(59, result.days.size)
    }

    @Test
    fun anUnchangedCalendarReplansToTheSameDays() {
        val eligible = pool(30) // 59 days from 30 words: new words first, then repeats
        val first = plan(eligible, seed = 1)
        val second = plan(eligible, previousAuto = first.autoPicks(), seed = 99)
        assertEquals(first.days, second.days)
    }

    @Test
    fun aManualPickOfARemovedOrIneligibleWordIsReplacedWithANotice() {
        val eligible = pool(80)
        val removedDay = today.plusDays(5)
        val ineligibleDay = today.plusDays(9)
        val keptDay = today.plusDays(12)
        val manual = mapOf(
            removedDay to ManualPick("id-gone", "gone", wordActive = false),
            ineligibleDay to ManualPick("id-unmarked", "unmarked", wordActive = true),
            keptDay to ManualPick("id-w7", "w7", wordActive = true),
        )

        val result = plan(eligible, manual = manual)

        assertEquals(
            listOf(
                CalendarEngine.ReplacedPick(removedDay, "gone", NoticeReason.REMOVED),
                CalendarEngine.ReplacedPick(ineligibleDay, "unmarked", NoticeReason.INELIGIBLE),
            ),
            result.replacedManual,
        )
        assertEquals(Source.AUTO, result.days.getValue(removedDay).source)
        assertEquals(Source.AUTO, result.days.getValue(ineligibleDay).source)
        assertEquals(Source.MANUAL, result.days.getValue(keptDay).source)
        assertEquals("w7", result.days.getValue(keptDay).candidate.text)
        assertEquals(1, result.days.values.count { it.candidate.text == "w7" }, "a manual word is not also picked automatically")
    }

    @Test
    fun takingAnotherDaysAutomaticWordRepicksOnlyThatDay() {
        val eligible = pool(80)
        val before = plan(eligible)
        val stolenDay = today.plusDays(20)
        val stolen = before.days.getValue(stolenDay).candidate
        val pickedDay = today.plusDays(4)

        val after = plan(
            eligible,
            manual = mapOf(pickedDay to ManualPick(stolen.id, stolen.text, wordActive = true)),
            previousAuto = before.autoPicks(),
            seed = 7,
        )

        assertEquals(stolen.text, after.days.getValue(pickedDay).candidate.text)
        assertEquals(Source.MANUAL, after.days.getValue(pickedDay).source)
        assertNotEquals(stolen.text, after.days.getValue(stolenDay).candidate.text, "the other day gets a new word")
        val changed = before.texts().filter { (day, text) -> after.texts()[day] != text }.keys
        assertEquals(setOf(pickedDay, stolenDay), changed)
        val texts = after.days.values.map { it.candidate.text }
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun aNeverUsedWordReplacesTheEarliestRepeat() {
        val frozen = mapOf(today.minusDays(1) to "alpha", today to "bravo", tomorrow to "charlie")
        val before = plan(words("alpha", "bravo", "charlie"), frozen = frozen)
        assertTrue(before.days.values.all { it.isRepeat })

        val after = plan(words("alpha", "bravo", "charlie", "delta"), frozen = frozen, previousAuto = before.autoPicks(), seed = 3)

        assertEquals("delta", after.days.getValue(firstOpen).candidate.text)
        assertFalse(after.days.getValue(firstOpen).isRepeat)
        assertEquals(1, after.days.values.count { !it.isRepeat }, "one new word replaces one repeat")
    }

    @Test
    fun wordsUsedBeforeTheHistoryStartCountAsNeverUsed() {
        val frozen = mapOf(today.minusDays(3) to "alpha", today.minusDays(2) to "bravo", today to "x1", tomorrow to "x2")

        val fromLaunch = plan(words("alpha", "bravo"), frozen = frozen, historyStart = today)
        assertEquals(setOf("alpha", "bravo"), fromLaunch.days.values.filter { !it.isRepeat }.map { it.candidate.text }.toSet())

        val countingEverything = plan(words("alpha", "bravo"), frozen = frozen, historyStart = today.minusDays(30))
        assertTrue(countingEverything.days.values.all { it.isRepeat })
    }

    @Test
    fun aManualPickBeyondTheHorizonIsNotScheduledEarlier() {
        val eligible = pool(59) // exactly enough for the horizon without w59...
        val farDay = today.plusDays(200)
        val result = plan(eligible, manual = mapOf(farDay to ManualPick("id-w59", "w59", wordActive = true)))

        assertEquals(Source.MANUAL, result.days.getValue(farDay).source)
        val beforeFar = result.days.filterKeys { it <= horizonEnd }
        assertTrue(beforeFar.values.none { it.candidate.text == "w59" && !it.isRepeat }, "w59 is reserved for its day")
        assertEquals(1, beforeFar.values.count { it.isRepeat }, "58 new words for 59 days: one repeat")
        assertFalse(today.plusDays(100) in result.days, "no automatic day past the horizon")
    }

    @Test
    fun anEditRepicksAutomaticDaysButAManualPickFollowsItsWord() {
        val eligible = pool(80)
        val manualDay = today.plusDays(3)
        val before = plan(eligible, manual = mapOf(manualDay to ManualPick("id-w1", "w1", wordActive = true)))
        val autoDay = before.days.entries.first { it.value.source == Source.AUTO && it.value.candidate.id != "id-w1" }
        val autoWord = autoDay.value.candidate

        // Both words respelled in the catalog: same ids, new text.
        val edited = eligible.map {
            when (it.id) {
                "id-w1" -> it.copy(text = "w1fixed")
                autoWord.id -> it.copy(text = "${autoWord.text}fixed")
                else -> it
            }
        }
        val after = plan(
            edited,
            manual = mapOf(manualDay to ManualPick("id-w1", "w1", wordActive = true)),
            previousAuto = before.autoPicks(),
            seed = 5,
        )

        assertEquals("w1fixed", after.days.getValue(manualDay).candidate.text)
        assertEquals(Source.MANUAL, after.days.getValue(manualDay).source)
        assertNotEquals(autoWord.text, after.days.getValue(autoDay.key).candidate.text, "the old spelling is gone")
        // Only the automatic day whose word was edited changed.
        val changed = before.texts().filter { (day, text) -> after.texts()[day] != text }.keys
        assertEquals(setOf(manualDay, autoDay.key), changed)
    }

    @Test
    fun uzbekPairsAreUsedByTheirLatinText() {
        val pairs = listOf(
            Candidate("pair-1", "kitob", "китоб"),
            Candidate("pair-2", "shahar", "шаҳар"),
            Candidate("pair-3", "qalam", "қалам"),
        )
        val frozen = mapOf(today.minusDays(1) to "shahar", today to "kitob", tomorrow to "legacy")
        val result = plan(pairs, frozen = frozen)

        val first = result.days.getValue(firstOpen)
        assertEquals("qalam", first.candidate.text, "the only never-used pair comes first")
        assertEquals("қалам", first.candidate.textCyrl)
        assertFalse(first.isRepeat)
        val second = result.days.getValue(firstOpen.plusDays(1))
        assertEquals("shahar", second.candidate.text)
        assertEquals(today.minusDays(1), second.lastUse)
    }

    @Test
    fun missingDaysAfterLongDowntimeAreFilledOnce() {
        val lastStored = today.minusDays(3)
        val frozen = mapOf(lastStored.minusDays(1) to "w1", lastStored to "w2")
        val result = plan(pool(80), frozen = frozen)

        assertEquals((-2L..60L).map { today.plusDays(it) }, result.days.keys.toList())
        assertTrue(listOf("w1", "w2").none { word -> result.days.values.any { it.candidate.text == word && !it.isRepeat } })
    }

    @Test
    fun anEmptyPoolSchedulesNothingButKeepsNoPicks() {
        val result = plan(emptyList(), manual = mapOf(today.plusDays(4) to ManualPick("id-x", "x", wordActive = true)))
        assertTrue(result.days.isEmpty())
        assertEquals(NoticeReason.INELIGIBLE, result.replacedManual.single().reason)
    }

    @Test
    fun aWordLeavingThePoolRepicksOnlyItsOwnDays() {
        val frozen = mapOf(today.minusDays(2) to "alpha", today.minusDays(1) to "bravo", today to "charlie", tomorrow to "delta")
        val eligible = words("alpha", "bravo", "charlie", "delta", "echo")
        val before = plan(eligible, frozen = frozen)
        assertEquals("echo", before.days.getValue(firstOpen).candidate.text, "the only never-used word comes first")
        val echoDays = before.days.filterValues { it.candidate.text == "echo" }.keys

        val after = plan(eligible.filter { it.text != "echo" }, frozen = frozen, previousAuto = before.autoPicks(), seed = 11)

        val changed = before.texts().filter { (day, text) -> after.texts()[day] != text }.keys
        assertEquals(echoDays, changed, "repeats around the re-picked days stay")
        assertTrue(after.days.values.none { it.candidate.text == "echo" })
        after.days.values.map { it.candidate.text }.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }
}
