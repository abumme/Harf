package uz.abumme.harfgame.feature.daily

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The word to solve today for one language. */
data class DailyPuzzle(
    val languageId: String,
    val epochDay: Long,
    val answer: List<String>,
) {
    val tileCount: Int get() = answer.size
}

/**
 * Deterministic, offline daily-word selection. Each language rolls over at local midnight
 * in its fixed timezone; the word is a stable function of the day number, so all devices
 * get the same word without any network call. Uzbek resolves one paired lexeme per script.
 */
@OptIn(ExperimentalTime::class)
class DailyPuzzleProvider(
    private val registry: LanguageRegistry,
    private val packs: WordPackRepository,
) {
    private val zones = mapOf(
        "uz-latn" to "Asia/Tashkent",
        "uz-cyrl" to "Asia/Tashkent",
        "en" to "Asia/Tashkent",
        "kk" to "Asia/Almaty",
        "ru" to "Europe/Moscow",
    )

    fun epochDay(languageId: String, instant: Instant): Long {
        val zone = TimeZone.of(zones[languageId] ?: "Asia/Tashkent")
        return instant.toLocalDateTime(zone).date.toEpochDays()
    }

    suspend fun daily(languageId: String, instant: Instant = Clock.System.now()): DailyPuzzle {
        val day = epochDay(languageId, instant)
        val answer = when (languageId) {
            "uz-latn", "uz-cyrl" -> {
                val lex = UzbekDailyWords.lexemes
                lex[day.mod(lex.size).toInt()].graphemes(languageId)
                    ?: error("No $languageId decomposition")
            }
            else -> {
                val pack = packs.load(languageId)
                require(pack.answers.isNotEmpty()) { "Empty answer pack: $languageId" }
                pack.answers[day.mod(pack.answers.size).toInt()]
            }
        }
        return DailyPuzzle(languageId, day, answer)
    }
}
