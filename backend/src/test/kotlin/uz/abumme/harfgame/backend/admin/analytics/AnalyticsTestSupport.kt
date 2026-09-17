package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.calendar.DailySettings
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.clearDailyCalendars
import uz.abumme.harfgame.backend.db.AccountEventsDailyTable
import uz.abumme.harfgame.backend.db.AnalyticsMetaTable
import uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.GameResultsTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.defaultAnswers
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Instant
import java.time.LocalDate

// Shared fixtures of the analytics tests.

val PACK_LANGS = listOf("en", "kk", "ru", "uz-cyrl", "uz-latn")

/**
 * Empties everything analytics reads or writes: accounts (with their identities, stats and game results), suggestions,
 * the catalog and calendars, staff and the audit log, and every analytics table; then leaves one tiny pack per launch
 * language (not carried into the catalog, so the fixture alone decides what `words` holds).
 */
fun resetAnalyticsData() {
    transaction(DatabaseFactory.init()) {
        AnalyticsRollupJob.ROLLUP_TABLES.forEach { it.deleteAll() }
        AnalyticsRollupDaysTable.deleteAll()
        AnalyticsMetaTable.deleteAll()
        AccountEventsDailyTable.deleteAll()
        StaffAuditLogTable.deleteAll()
        clearDailyCalendars()
        WordsTable.deleteAll()
        WordSuggestionsTable.deleteAll()
        StaffSessionsTable.deleteAll()
        StaffLanguagesTable.deleteAll()
        StaffTable.deleteAll()
        UsersTable.deleteAll()
        WordPacksTable.deleteAll()
        for (lang in PACK_LANGS) {
            val words = Json.encodeToString(defaultAnswers(lang))
            WordPacksTable.insert {
                it[WordPacksTable.lang] = lang
                it[version] = "1"
                it[effectiveFrom] = 0L
                it[anchorEpochDay] = 0L
                it[answers] = words
                it[guesses] = words
                it[schedule] = words
                it[updatedAt] = Instant.now()
            }
        }
    }
}

/** A catalog whose calendars read no environment. */
fun analyticsCatalog(clock: MutableClock) = WordCatalogService(WordPackServerService(), clock, daily = DailySettings())

/** A rollup job with no per-tick cap unless [cap] is given. */
fun rollupJob(clock: MutableClock, cap: Int = Int.MAX_VALUE, batchSize: Int = 500, maxBatches: Int = 20) =
    AnalyticsRollupJob(
        analyticsCatalog(clock),
        ResultsSweep(GameResultRecorder(clock), batchSize = batchSize, maxBatches = maxBatches),
        maxGroupDaysPerTick = cap,
    )

fun AnalyticsRollupJob.run(now: Instant): AnalyticsRollupJob.Report = runBlocking { runOnce(now) }

/** Inserts a player account (anonymous unless identities are added separately). */
fun insertAccount(id: String, createdAt: Instant = Instant.parse("2026-04-30T08:00:00Z"), name: String? = null) {
    transaction(DatabaseFactory.init()) {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.createdAt] = createdAt
            it[UsersTable.name] = name
        }
    }
}

/** Inserts game results of [userId] directly (no plausibility filter). */
fun insertResults(userId: String, lang: String, days: Iterable<Long>, won: Boolean = true, attempts: Int = 3, receivedAt: Instant = Instant.parse("2026-09-01T00:00:00Z")) {
    transaction(DatabaseFactory.init()) {
        GameResultsTable.batchInsert(days.toList(), shouldReturnGeneratedValues = false) { day ->
            this[GameResultsTable.userId] = userId
            this[GameResultsTable.lang] = lang
            this[GameResultsTable.puzzleDay] = day
            this[GameResultsTable.won] = won
            this[GameResultsTable.attempts] = if (won) attempts else 6
            this[GameResultsTable.receivedAt] = receivedAt
        }
    }
}

/** An account's stored game results, as records. */
fun storedResults(userId: String): List<ResultRecordDto> = transaction(DatabaseFactory.init()) {
    GameResultsTable.selectAll().where { GameResultsTable.userId eq userId }
        .map { ResultRecordDto(it[GameResultsTable.lang], it[GameResultsTable.puzzleDay], it[GameResultsTable.won], it[GameResultsTable.attempts]) }
        .sortedWith(compareBy({ it.language }, { it.puzzleDay }))
}

fun gameResultCount(): Long = transaction(DatabaseFactory.init()) { GameResultsTable.selectAll().count() }

/** Stores [records] as [userId]'s snapshot at [updatedAt]. */
fun insertSnapshot(userId: String, records: List<ResultRecordDto>, updatedAt: Instant) {
    transaction(DatabaseFactory.init()) {
        UserStatsTable.insert {
            it[UserStatsTable.userId] = userId
            it[data] = Json.encodeToString(records)
            it[UserStatsTable.updatedAt] = updatedAt
        }
    }
}

/** The single row of [table] matching [where], or null. */
fun <T : Table> rowOf(table: T, where: T.() -> org.jetbrains.exposed.v1.core.Op<Boolean>) = transaction(DatabaseFactory.init()) {
    table.selectAll().where { table.where() }.singleOrNull()
}

fun rollupDay(group: String, day: Long) = rowOf(AnalyticsRollupDaysTable) { (grp eq group) and (AnalyticsRollupDaysTable.day eq day) }

fun deletionsOn(date: LocalDate): Int? = rowOf(AccountEventsDailyTable) { AccountEventsDailyTable.date eq date }?.get(AccountEventsDailyTable.deletions)
