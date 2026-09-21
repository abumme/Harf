package uz.abumme.harfgame.backend

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.CalendarNoticesTable
import uz.abumme.harfgame.backend.db.CalendarStateTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.SuggestionReportsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.util.UUID

// Shared database fixtures for the suggestion, review-worker and daily-report tests.

/**
 * Empties the daily-word calendars: days, notices, first-run markers and Uzbek pairs. Call inside a transaction before
 * deleting catalog words (pairs reference them).
 */
internal fun clearDailyCalendars() {
    DailyWordsTable.deleteAll()
    CalendarNoticesTable.deleteAll()
    CalendarStateTable.deleteAll()
    LexemePairsTable.deleteAll()
}

/**
 * Clears suggestion-related tables, the word catalog, staff accounts and the audit log, and seeds author account `u1`
 * (no display name).
 */
internal fun resetSuggestionData() {
    transaction(DatabaseFactory.init()) {
        StaffAuditLogTable.deleteAll()
        clearDailyCalendars()
        WordsTable.deleteAll()
        SuggestionReportsTable.deleteAll()
        WordSuggestionsTable.deleteAll()
        WordPacksTable.deleteAll()
        StaffSessionsTable.deleteAll()
        StaffLanguagesTable.deleteAll()
        StaffTable.deleteAll()
        UsersTable.deleteAll()
        UsersTable.insert { it[id] = "u1"; it[createdAt] = Instant.now() }
    }
}

/**
 * A daily answer per language (a word no test suggests), so a fixture pack passes the app's integrity check whenever
 * the catalog publishes it.
 */
internal fun defaultAnswers(lang: String): List<String> = listOfNotNull(
    mapOf("en" to "wharf", "ru" to "берег", "kk" to "өзен", "uz-latn" to "daryo", "uz-cyrl" to "дарё")[lang],
)

/**
 * Inserts a stored pack and, unless [catalog] is false, carries it into the word catalog the way startup does, so
 * catalog writes (accepted suggestions, merges) start from these words.
 */
internal fun insertPack(
    lang: String,
    version: String,
    guesses: List<String>,
    answers: List<String> = defaultAnswers(lang),
    schedule: List<String> = answers,
    effectiveFrom: Long = 0L,
    catalog: Boolean = true,
) {
    transaction(DatabaseFactory.init()) {
        WordPacksTable.insert {
            it[WordPacksTable.lang] = lang
            it[WordPacksTable.version] = version
            it[WordPacksTable.effectiveFrom] = effectiveFrom
            it[WordPacksTable.anchorEpochDay] = 0L
            it[WordPacksTable.answers] = Json.encodeToString(answers)
            it[WordPacksTable.guesses] = Json.encodeToString(guesses)
            it[WordPacksTable.schedule] = Json.encodeToString(schedule)
            it[WordPacksTable.updatedAt] = Instant.now()
        }
    }
    if (catalog) runBlocking { WordCatalogService(WordPackServerService()).carryOver() }
}

/** The catalog row of [text] in [lang], or null. */
internal fun wordRow(lang: String, text: String) = transaction(DatabaseFactory.init()) {
    WordsTable.selectAll().where { (WordsTable.lang eq lang) and (WordsTable.text eq text) }.singleOrNull()
}

/** Inserts a suggestion row directly. Review columns left NULL describe a row that predates the review worker. */
internal fun insertSuggestion(
    lang: String,
    word: String,
    status: SuggestionStatus = SuggestionStatus.PENDING,
    reviewState: String? = null,
    decidedVia: String? = null,
    decidedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    author: String? = "u1",
): String {
    val id = UUID.randomUUID().toString()
    transaction(DatabaseFactory.init()) {
        WordSuggestionsTable.insert {
            it[WordSuggestionsTable.id] = id
            it[WordSuggestionsTable.lang] = lang
            it[WordSuggestionsTable.word] = word
            it[suggestedBy] = author
            it[WordSuggestionsTable.status] = status.name
            it[WordSuggestionsTable.createdAt] = createdAt
            it[WordSuggestionsTable.reviewState] = reviewState
            it[WordSuggestionsTable.decidedVia] = decidedVia
            it[WordSuggestionsTable.decidedAt] = decidedAt
        }
    }
    return id
}

internal fun insertPending(lang: String, word: String, author: String? = "u1"): String =
    insertSuggestion(lang, word, author = author)

internal fun <T> suggestionColumn(id: String, column: Column<T>): T = transaction(DatabaseFactory.init()) {
    WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq id }.single()[column]
}

internal fun statusOf(id: String): String = suggestionColumn(id, WordSuggestionsTable.status)
