package uz.abumme.harfgame.backend

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.SuggestionReportsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.util.UUID

// Shared database fixtures for the suggestion, review-worker and daily-report tests.

/** Clears suggestion-related tables and seeds author account `u1` (no display name). */
internal fun resetSuggestionData() {
    transaction(DatabaseFactory.init()) {
        SuggestionReportsTable.deleteAll()
        WordSuggestionsTable.deleteAll()
        WordPacksTable.deleteAll()
        UsersTable.deleteAll()
        UsersTable.insert { it[id] = "u1"; it[createdAt] = Instant.now() }
    }
}

internal fun insertPack(lang: String, version: String, guesses: List<String>) {
    transaction(DatabaseFactory.init()) {
        WordPacksTable.insert {
            it[WordPacksTable.lang] = lang
            it[WordPacksTable.version] = version
            it[effectiveFrom] = 0L
            it[anchorEpochDay] = 0L
            it[answers] = Json.encodeToString(listOf<String>())
            it[WordPacksTable.guesses] = Json.encodeToString(guesses)
            it[schedule] = Json.encodeToString(listOf<String>())
            it[updatedAt] = Instant.now()
        }
    }
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
