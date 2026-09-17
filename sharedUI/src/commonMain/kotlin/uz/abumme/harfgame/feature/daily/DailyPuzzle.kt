package uz.abumme.harfgame.feature.daily

import kotlin.time.Clock
import kotlin.time.Instant
import uz.abumme.harfgame.data.wordpack.FirstPackSync
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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
 * in its fixed timezone; the word is the published calendar's word for that day (cached server
 * pack, else the build's calendar snapshot, else the generated baseline), so devices on the same
 * pack version get the same word without any network call. Uzbek resolves one paired lexeme per
 * script.
 *
 * A language without a cached server pack (a fresh install) first waits up to [FIRST_SYNC_WAIT]
 * for its first pack sync of the session, so an online fresh install plays the server's word;
 * offline, the wait ends silently and the bundled pack is used.
 */
@OptIn(ExperimentalTime::class)
class DailyPuzzleProvider(
    private val registry: LanguageRegistry,
    private val packs: WordPackRepository,
    private val firstSync: FirstPackSync? = null,
) {
    /** The puzzle day of [instant]; the per-language zones are shared with the server's calendar ([PuzzleDays]). */
    fun epochDay(languageId: String, instant: Instant): Long = PuzzleDays.epochDay(languageId, instant)

    suspend fun daily(languageId: String, instant: Instant = Clock.System.now()): DailyPuzzle {
        val day = epochDay(languageId, instant)
        firstSync?.let { sync ->
            if (!sync.hasCachedPack(languageId)) sync.awaitFirstSync(languageId, FIRST_SYNC_WAIT)
        }
        val pack = packs.load(languageId)
        val answer = if (pack.schedule.isNotEmpty()) {
            // schedule already resolves Uzbek to the right script; anchored + immutable past.
            WordPackSchedule.answerFor(pack.schedule, pack.anchorEpochDay, day)
        } else {
            require(pack.answers.isNotEmpty()) { "Empty answer pack: $languageId" }
            pack.answers[day.mod(pack.answers.size).toInt()]
        }
        return DailyPuzzle(languageId, day, answer)
    }

    companion object {
        /** The longest a fresh install waits for its first pack sync before playing the bundled pack. */
        val FIRST_SYNC_WAIT = 2.seconds
    }
}
