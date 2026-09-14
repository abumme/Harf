package uz.abumme.harfgame.backend.service

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.SuggestionReportsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Result of attempting to store a suggestion. The route maps these to HTTP status codes. */
sealed interface SuggestOutcome {
    /** A new pending suggestion was stored and queued for the review worker. */
    data class Stored(val id: String) : SuggestOutcome
    /** An identical pending suggestion already existed; reported as success, nothing new stored. */
    data object DuplicatePending : SuggestOutcome
    /** Failed validation (ill-formed, offensive, gibberish, or already in the pack). */
    data class Rejected(val reason: String) : SuggestOutcome
    /** The author exceeded the per-day cap. */
    data object OverCap : SuggestOutcome
}

/** Result of an editor decision on a suggestion. */
sealed interface DecideOutcome {
    data class Applied(val accepted: Boolean, val lang: String, val word: String) : DecideOutcome
    data object NotFound : DecideOutcome
    data object AlreadyDecided : DecideOutcome
}

/** Background review progress of a suggestion (`word_suggestions.review_state`). */
enum class ReviewState { QUEUED, POSTED }

/** Who decided a suggestion (`word_suggestions.decided_via`). */
enum class DecidedVia { AUTO, EDITOR }

/** A suggestion awaiting the review worker. */
data class QueuedSuggestion(
    val id: String,
    val lang: String,
    val word: String,
    val suggestedBy: String?,
    val status: SuggestionStatus,
    val lookupAttempts: Int,
    /** Set once the suggestion was accepted automatically. */
    val autoForm: WordForm?,
)

/** One language's decisions within a report window, plus its suggestions still pending when queried. */
data class DailySummary(
    val autoAccepted: List<String>,
    val editorAccepted: List<String>,
    val rejected: List<String>,
    val pending: Long,
)

/**
 * Validates and stores word suggestions. Validation is string-level: the backend has no grapheme
 * tokenizer (that lives in the client's :sharedUI), so length is bounded by characters, not
 * graphemes. These heuristics only cut obvious noise — editors decide whether a word is real.
 * ponytail: char-length + gibberish heuristics; move to grapheme-exact only if a shared tokenizer
 * is extracted to :sharedData.
 */
class SuggestionServerService(
    private val wordPackService: WordPackServerService,
    private val dailyCap: Int = DEFAULT_DAILY_CAP,
) {
    suspend fun suggest(userId: String, langRaw: String, wordRaw: String): SuggestOutcome {
        val lang = langRaw.trim()
        val word = wordRaw.trim().lowercase()

        if (lang.isEmpty()) return SuggestOutcome.Rejected("bad_lang")
        if (word.length !in MIN_CHARS..MAX_CHARS) return SuggestOutcome.Rejected("bad_length")
        if (word in offensiveWords(lang)) return SuggestOutcome.Rejected("offensive")
        gibberishReason(word)?.let { return SuggestOutcome.Rejected(it) }
        if (isAlreadyInPack(lang, word)) return SuggestOutcome.Rejected("already_present")

        return DatabaseFactory.dbQuery {
            val hasPending = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.lang eq lang) and
                    (WordSuggestionsTable.word eq word) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }.any()
            if (hasPending) return@dbQuery SuggestOutcome.DuplicatePending

            val since = Instant.ofEpochMilli(System.currentTimeMillis() - DAY_MILLIS)
            val recent = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.suggestedBy eq userId) and
                    (WordSuggestionsTable.createdAt greater since)
            }.count()
            if (recent >= dailyCap) return@dbQuery SuggestOutcome.OverCap

            val id = UUID.randomUUID().toString()
            WordSuggestionsTable.insert {
                it[WordSuggestionsTable.id] = id
                it[WordSuggestionsTable.lang] = lang
                it[WordSuggestionsTable.word] = word
                it[suggestedBy] = userId
                it[status] = SuggestionStatus.PENDING.name
                it[createdAt] = Instant.now()
                it[reviewState] = ReviewState.QUEUED.name
            }
            SuggestOutcome.Stored(id)
        }
    }

    /** Human label for a suggestion's author: the display name, else "Аноним" (also for a deleted account). */
    suspend fun authorLabel(userId: String?): String = DatabaseFactory.dbQuery {
        userId?.let { UsersTable.selectAll().where { UsersTable.id eq it }.singleOrNull()?.get(UsersTable.name) }
            ?.takeIf { it.isNotBlank() }
            ?: ANONYMOUS
    }

    /**
     * Apply a decision. Acts only while the suggestion is still PENDING (idempotent against a double-tap,
     * a second editor, or a racing automatic acceptance). On accept, the word is added to the pack.
     * Authorization of an editor is the caller's responsibility (the Telegram layer checks the allowlist).
     * An automatic acceptance passes the verified [form], kept for its announcement.
     */
    suspend fun decide(
        suggestionId: String,
        accept: Boolean,
        editor: String,
        via: DecidedVia = DecidedVia.EDITOR,
        form: WordForm? = null,
    ): DecideOutcome {
        val newStatus = if (accept) SuggestionStatus.ACCEPTED else SuggestionStatus.REJECTED
        // One conditional UPDATE decides the race. READ COMMITTED makes a concurrent decision re-check
        // `status = PENDING` after waiting for the row lock (and update nothing) instead of failing with
        // the serialization error the pool's default REPEATABLE READ raises.
        val outcome = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            val updated = WordSuggestionsTable.update({
                (WordSuggestionsTable.id eq suggestionId) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }) {
                it[status] = newStatus.name
                it[decidedBy] = editor
                it[decidedVia] = via.name
                it[autoForm] = form?.name
                it[decidedAt] = Instant.now()
            }
            val row = WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }.singleOrNull()
            when {
                row == null -> DecideOutcome.NotFound
                updated == 0 -> DecideOutcome.AlreadyDecided
                else -> DecideOutcome.Applied(accept, row[WordSuggestionsTable.lang], row[WordSuggestionsTable.word])
            }
        }
        if (outcome is DecideOutcome.Applied && accept) wordPackService.addGuess(outcome.lang, outcome.word)
        return outcome
    }

    /** Suggestions awaiting the review worker, oldest first. Rows that predate the worker are never returned. */
    suspend fun queued(limit: Int): List<QueuedSuggestion> = DatabaseFactory.dbQuery {
        WordSuggestionsTable.selectAll()
            .where { WordSuggestionsTable.reviewState eq ReviewState.QUEUED.name }
            .orderBy(WordSuggestionsTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { row ->
                QueuedSuggestion(
                    id = row[WordSuggestionsTable.id],
                    lang = row[WordSuggestionsTable.lang],
                    word = row[WordSuggestionsTable.word],
                    suggestedBy = row[WordSuggestionsTable.suggestedBy],
                    status = SuggestionStatus.valueOf(row[WordSuggestionsTable.status]),
                    lookupAttempts = row[WordSuggestionsTable.lookupAttempts] ?: 0,
                    autoForm = row[WordSuggestionsTable.autoForm]?.let(WordForm::valueOf),
                )
            }
    }

    /** Record that Telegram confirmed the suggestion's message; the worker stops selecting it. */
    suspend fun markPosted(suggestionId: String) {
        DatabaseFactory.dbQuery {
            WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) {
                it[reviewState] = ReviewState.POSTED.name
            }
        }
    }

    /** Count one more failed dictionary lookup; returns the new total. */
    suspend fun incrementLookupAttempts(suggestionId: String): Int = DatabaseFactory.dbQuery {
        val attempts = (WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }
            .single()[WordSuggestionsTable.lookupAttempts] ?: 0) + 1
        WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) { it[lookupAttempts] = attempts }
        attempts
    }

    /**
     * [lang]'s decisions with `decided_at` in the half-open window [[from], [to]), in decision order, plus
     * its suggestions still pending now. Decisions with no recorded source (made before automatic
     * acceptance existed) count as editor decisions.
     */
    suspend fun dailySummary(lang: String, from: Instant, to: Instant): DailySummary = DatabaseFactory.dbQuery {
        val decided = WordSuggestionsTable.selectAll().where {
            (WordSuggestionsTable.lang eq lang) and
                (WordSuggestionsTable.decidedAt greaterEq from) and
                (WordSuggestionsTable.decidedAt less to)
        }.orderBy(WordSuggestionsTable.decidedAt, SortOrder.ASC).toList()

        fun words(predicate: (ResultRow) -> Boolean) = decided.filter(predicate).map { it[WordSuggestionsTable.word] }
        fun ResultRow.isAccepted() = this[WordSuggestionsTable.status] == SuggestionStatus.ACCEPTED.name
        fun ResultRow.isAuto() = this[WordSuggestionsTable.decidedVia] == DecidedVia.AUTO.name

        DailySummary(
            autoAccepted = words { it.isAccepted() && it.isAuto() },
            editorAccepted = words { it.isAccepted() && !it.isAuto() },
            rejected = words { it[WordSuggestionsTable.status] == SuggestionStatus.REJECTED.name },
            pending = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.lang eq lang) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }.count(),
        )
    }

    /** Whether [lang]'s report for [day] was already delivered or settled as quiet. */
    suspend fun reportRecorded(lang: String, day: LocalDate): Boolean = DatabaseFactory.dbQuery {
        SuggestionReportsTable.selectAll()
            .where { (SuggestionReportsTable.lang eq lang) and (SuggestionReportsTable.day eq day) }
            .any()
    }

    /** Settle [lang]'s report for [day] so it is never sent again. */
    suspend fun recordReport(lang: String, day: LocalDate) {
        DatabaseFactory.dbQuery {
            SuggestionReportsTable.insertIgnore {
                it[SuggestionReportsTable.lang] = lang
                it[SuggestionReportsTable.day] = day
                it[sentAt] = Instant.now()
            }
        }
    }

    private suspend fun isAlreadyInPack(lang: String, word: String): Boolean {
        val pack = wordPackService.getPack(lang) ?: return false
        return pack.guesses.any { it.trim().lowercase() == word }
    }

    /** Cheap gibberish checks. Returns a reason string when the word looks like junk, else null. */
    private fun gibberishReason(word: String): String? {
        // three or more of the same character in a row: "aaa", "sssalom"
        var run = 1
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) {
                run++
                if (run >= 3) return "repeat_run"
            } else run = 1
        }
        if (word.toSet().size < MIN_DISTINCT) return "too_few_distinct"
        if (word.none { it in VOWELS }) return "no_vowel"
        return null
    }

    private fun offensiveWords(lang: String): Set<String> {
        val stream = javaClass.getResourceAsStream("/blocklists/${lang}_block.txt") ?: return emptySet()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
    }

    companion object {
        const val DEFAULT_DAILY_CAP = 10
        private const val ANONYMOUS = "Аноним"
        private const val MIN_CHARS = 2
        private const val MAX_CHARS = 24
        private const val MIN_DISTINCT = 3
        private const val DAY_MILLIS = 24L * 3600 * 1000
        // Latin + Cyrillic (ru/kk) + Uzbek vowels. Broad on purpose — a false "has vowel" is safe.
        private val VOWELS = "aeiouyаеёиоуыэюяәөүұіи".toSet()
    }
}
