package uz.abumme.harfgame.data.wordpack

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Where a puzzle day starts: each language rolls over at midnight in its own fixed timezone. The app's daily word, the
 * server's daily-word calendar and the staff panel all count days with this one table, so they never disagree on which
 * day it is.
 */
@OptIn(ExperimentalTime::class)
object PuzzleDays {
    /** The zone of a language without its own entry. */
    const val DEFAULT_ZONE = "Asia/Tashkent"

    private val zones = mapOf(
        "uz-latn" to "Asia/Tashkent",
        "uz-cyrl" to "Asia/Tashkent",
        "en" to "Asia/Tashkent",
        "kk" to "Asia/Almaty",
        "ru" to "Europe/Moscow",
    )

    /** Every zone a puzzle day starts in (the default included): a day has ended everywhere once it ended in each. */
    val allZones: List<String> = (zones.values + DEFAULT_ZONE).distinct().sorted()

    /** The IANA timezone id whose midnight starts [languageId]'s puzzle day. */
    fun zoneOf(languageId: String): String = zones[languageId] ?: DEFAULT_ZONE

    /** The epoch day of [instant] in [languageId]'s timezone. */
    fun epochDay(languageId: String, instant: Instant): Long =
        instant.toLocalDateTime(TimeZone.of(zoneOf(languageId))).date.toEpochDays().toLong()
}
