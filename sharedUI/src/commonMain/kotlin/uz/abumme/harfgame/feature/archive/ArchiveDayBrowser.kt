package uz.abumme.harfgame.feature.archive

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
}
