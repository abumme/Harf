package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.batchInsert
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.GameResultsTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.sync.ResultRecordDto
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Which uploaded result records analytics believes. A record is dropped (never the upload) when its language has no
 * word pack, its attempts are outside `1..`[MAX_ATTEMPTS], or its puzzle day is after that language's current puzzle
 * day.
 */
object ResultPlausibility {
    const val MAX_ATTEMPTS = 6

    fun isPlausible(record: ResultRecordDto, packLanguages: Set<String>, now: Instant): Boolean =
        record.language in packLanguages &&
            record.attempts in 1..MAX_ATTEMPTS &&
            record.puzzleDay <= AnalyticsDays.puzzleToday(record.language, now)

    /** The plausible records of one snapshot, one per language and puzzle day (the first one wins, as it does stored). */
    fun plausible(records: List<ResultRecordDto>, packLanguages: Set<String>, now: Instant): List<ResultRecordDto> =
        records.asSequence()
            .filter { isPlausible(it, packLanguages, now) }
            .distinctBy { it.language to it.puzzleDay }
            .toList()
}

/** The languages that have a word pack, re-read at most every [ttl]: every upload asks. */
class PackLanguages(
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(5),
    private val load: suspend () -> Set<String> = {
        DatabaseFactory.dbQuery { WordPacksTable.select(WordPacksTable.lang).mapTo(HashSet()) { it[WordPacksTable.lang] } }
    },
) {
    @Volatile
    private var cached: Pair<Instant, Set<String>>? = null

    suspend fun get(): Set<String> {
        val now = clock.instant()
        cached?.let { (loadedAt, languages) -> if (now.isBefore(loadedAt.plus(ttl))) return languages }
        return load().also { cached = now to it }
    }
}

/**
 * Records synced results as `game_results` rows: insert-ignore, so recording is idempotent, the first result for an
 * account, language and puzzle day wins, and a later snapshot that lacks a result never removes it.
 */
class GameResultRecorder(
    private val clock: Clock = Clock.systemUTC(),
    private val packLanguages: PackLanguages = PackLanguages(clock),
) {
    /**
     * Records [records] of an accepted upload by [userId] in their own transaction. A failure is logged and swallowed:
     * the upload's response never depends on analytics (the sweep repairs what an accepted snapshot missed).
     */
    suspend fun recordUpload(userId: String, records: List<ResultRecordDto>) {
        if (records.isEmpty()) return
        try {
            val now = clock.instant()
            val valid = ResultPlausibility.plausible(records, packLanguages.get(), now)
            if (valid.isEmpty()) return
            DatabaseFactory.dbQuery { insertIgnore(userId, valid, now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("Analytics: could not record ${records.size} results of an upload: ${e.message}")
        }
    }

    /** The languages records must belong to (cached); the sweep filters with the same rules. */
    suspend fun packLanguages(): Set<String> = packLanguages.get()

    companion object {
        /** Inserts [records] (already filtered) for [userId], skipping keys already stored. Inside a transaction. */
        fun insertIgnore(userId: String, records: List<ResultRecordDto>, receivedAt: Instant) {
            if (records.isEmpty()) return
            GameResultsTable.batchInsert(records, ignore = true, shouldReturnGeneratedValues = false) { record ->
                this[GameResultsTable.userId] = userId
                this[GameResultsTable.lang] = record.language
                this[GameResultsTable.puzzleDay] = record.puzzleDay
                this[GameResultsTable.won] = record.won
                this[GameResultsTable.attempts] = record.attempts
                this[GameResultsTable.receivedAt] = receivedAt
            }
        }
    }
}
