package uz.abumme.harfgame.feature.archive

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import uz.abumme.harfgame.data.archive.ArchiveRunDto
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.sync.RoundKind
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object ArchiveDayBrowser {

    @OptIn(ExperimentalTime::class)
    suspend fun availableDays(
        provider: DailyPuzzleProvider,
        languageId: String,
        now: Instant = Clock.System.now(),
    ): List<Long> {
        val firstDay = provider.firstPublicDay(languageId)
        val today = provider.epochDay(languageId, now)
        if (today <= firstDay) return emptyList()
        return (firstDay until today).toList()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun isDayAvailable(
        provider: DailyPuzzleProvider,
        languageId: String,
        epochDay: Long,
        now: Instant = Clock.System.now(),
    ): Boolean {
        val firstDay = provider.firstPublicDay(languageId)
        val today = provider.epochDay(languageId, now)
        return epochDay in firstDay until today
    }

    /** A day's outcome as its archive row shows it. */
    data class DayResult(val won: Boolean, val attempts: Int)

    /**
     * Each played day's outcome for [languageId]: the latest archive playthrough, else the daily played on that day.
     * Uzbek counts both scripts, which share one lexeme per day. A day missing from the map is not played yet.
     */
    fun results(languageId: String, runs: List<ArchiveRunDto>, records: List<ResultRecord>): Map<Long, DayResult> {
        fun same(id: String) = id == languageId || (id.startsWith("uz") && languageId.startsWith("uz"))
        val out = HashMap<Long, DayResult>()
        records.filter { it.roundKind == RoundKind.OFFICIAL && same(it.language) }
            .forEach { out[it.puzzleDay] = DayResult(it.won, it.attempts) }
        runs.filter { same(it.language) }.sortedBy { it.completedAt }
            .forEach { out[it.puzzleDay] = DayResult(it.won, it.attempts) }
        return out
    }

    /** The calendar date of a puzzle day (puzzle days are dates in the language's own timezone). */
    fun dateLabel(epochDay: Long): String {
        val d = LocalDate.fromEpochDays(epochDay)
        return "${d.day.toString().padStart(2, '0')}.${d.month.number.toString().padStart(2, '0')}.${d.year}"
    }
}
