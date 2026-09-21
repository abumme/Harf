package uz.abumme.harfgame.backend.admin.analytics

import uz.abumme.harfgame.data.wordpack.PuzzleDays
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The days analytics counts in, from one instant.
 *
 * - **Result metrics** use the puzzle day (an epoch day in the language's own timezone, the app's [PuzzleDays] table).
 *   Puzzle day `d` of a language closes at the end of `d` in its zone; a cross-language day closes once it has ended in
 *   every zone.
 * - **Event metrics** (accounts, suggestions, content, staff) use the Asia/Tashkent date of the event.
 *
 * A closed day stays provisional for [SETTLE] after it closed, then it is final.
 */
object AnalyticsDays {
    /** The team's clock and the Telegram daily report's: every event metric's date. */
    val EVENTS_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

    /** How long a closed day keeps absorbing late syncs before it is final. */
    val SETTLE: Duration = Duration.ofDays(7)

    /** Analytics history starts with the daily puzzle itself: no rollup covers a day before the schedule anchor. */
    val HISTORY_START_DAY: Long = WordPackSchedule.ANCHOR_EPOCH_DAY

    /** [lang]'s current puzzle day at [now]. */
    fun puzzleToday(lang: String, now: Instant): Long = LocalDate.ofInstant(now, zoneOf(lang)).toEpochDay()

    /** The latest closed puzzle day of [lang] at [now]: every day up to and including it has ended in [lang]'s zone. */
    fun closedPuzzleDays(lang: String, now: Instant): Long = puzzleToday(lang, now) - 1

    /** The latest puzzle day that has ended in every language's zone at [now] (the westernmost zone decides). */
    fun closedGlobalPuzzleDay(now: Instant): Long =
        PuzzleDays.allZones.minOf { LocalDate.ofInstant(now, ZoneId.of(it)).toEpochDay() } - 1

    /** The latest closed Asia/Tashkent date at [now]. */
    fun closedEventDates(now: Instant): LocalDate = LocalDate.ofInstant(now, EVENTS_ZONE).minusDays(1)

    /** When [lang]'s puzzle day [day] ends. */
    fun closeOfPuzzleDay(lang: String, day: Long): Instant = LocalDate.ofEpochDay(day + 1).atStartOfDay(zoneOf(lang)).toInstant()

    /** When puzzle day [day] has ended in every zone. */
    fun closeOfGlobalDay(day: Long): Instant =
        PuzzleDays.allZones.maxOf { LocalDate.ofEpochDay(day + 1).atStartOfDay(ZoneId.of(it)).toInstant() }

    /** The start of Asia/Tashkent date [date]. */
    fun startOfEventDate(date: LocalDate): Instant = date.atStartOfDay(EVENTS_ZONE).toInstant()

    /** The end of Asia/Tashkent date [date] (exclusive). */
    fun endOfEventDate(date: LocalDate): Instant = startOfEventDate(date.plusDays(1))

    /** Whether a day that closed at [close] is past its settle window at [now]. */
    fun isFinal(close: Instant, now: Instant): Boolean = !now.isBefore(close.plus(SETTLE))

    private fun zoneOf(lang: String): ZoneId = ZoneId.of(PuzzleDays.zoneOf(lang))
}
