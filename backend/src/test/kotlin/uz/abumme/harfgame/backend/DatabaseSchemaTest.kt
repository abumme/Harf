package uz.abumme.harfgame.backend

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsRollupJob
import uz.abumme.harfgame.backend.db.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseSchemaTest {
    @Test
    fun testSchemaCreation() {
        val db = DatabaseFactory.init()
        transaction(db) {
            assertTrue(UsersTable.exists())
            assertTrue(OAuthIdentitiesTable.exists())
            assertTrue(RefreshTokensTable.exists())
            assertTrue(UserStatsTable.exists())
            assertTrue(WordSuggestionsTable.exists())
            assertTrue(SuggestionReportsTable.exists())
        }
    }

    @Test
    fun staffTablesAreCreatedFreshWithUniqueIndexesAndMigrateOnlyOnce() {
        val db = DatabaseFactory.init()
        // Make the staff tables fresh: drop them (and the word catalog that references staff), as on a database that
        // predates the admin panel.
        transaction(db) {
            exec("DROP TABLE IF EXISTS lexeme_pairs, words, staff_audit_log, staff_sessions, staff_languages, staff CASCADE")
            db.dialectMetadata.resetCaches()
        }

        DatabaseFactory.migrate(db)

        transaction(db) {
            assertTrue(StaffTable.exists())
            assertTrue(StaffLanguagesTable.exists())
            assertTrue(StaffSessionsTable.exists())
            assertTrue(StaffAuditLogTable.exists())

            val uniqueIndexes = mutableSetOf<String>()
            exec(
                "SELECT indexname FROM pg_indexes WHERE tablename IN ('staff', 'staff_sessions') " +
                    "AND indexdef LIKE 'CREATE UNIQUE INDEX%'"
            ) { rs -> while (rs.next()) uniqueIndexes += rs.getString(1) }
            assertTrue(
                uniqueIndexes.containsAll(
                    setOf("idx_staff_username", "idx_staff_telegram_user_id", "idx_staff_sessions_token_hash")
                ),
                "unique indexes: $uniqueIndexes",
            )

            val auditIndexes = mutableSetOf<String>()
            exec("SELECT indexname FROM pg_indexes WHERE tablename = 'staff_audit_log'") { rs ->
                while (rs.next()) auditIndexes += rs.getString(1)
            }
            assertTrue(
                auditIndexes.containsAll(setOf("idx_staff_audit_at", "idx_staff_audit_actor_at", "idx_staff_audit_lang_at")),
                "audit indexes: $auditIndexes",
            )
        }

        // A second startup finds nothing left to do.
        transaction(db) { assertEquals(emptyList(), DatabaseFactory.pendingMigrationStatements()) }
    }

    @Test
    fun wordCatalogIsAddedToADatabaseThatPredatesItAndOldSuggestionsReadBackWithEmptyNewColumns() {
        val db = DatabaseFactory.init()
        val id = java.util.UUID.randomUUID().toString()
        // A database from before the word catalog: no words table, no Telegram/review columns, one old suggestion.
        transaction(db) {
            exec("DROP TABLE IF EXISTS lexeme_pairs, words CASCADE")
            exec(
                "ALTER TABLE word_suggestions DROP COLUMN IF EXISTS telegram_chat_id, DROP COLUMN IF EXISTS telegram_message_id, " +
                    "DROP COLUMN IF EXISTS telegram_text, DROP COLUMN IF EXISTS review_reason"
            )
            exec(
                "INSERT INTO word_suggestions (id, lang, word, status, created_at, review_state) " +
                    "VALUES ('$id', 'en', 'older', 'PENDING', now(), 'POSTED')"
            )
            db.dialectMetadata.resetCaches()
        }

        DatabaseFactory.migrate(db)

        transaction(db) {
            assertTrue(WordsTable.exists())
            val indexes = mutableMapOf<String, String>()
            exec("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'words'") { rs ->
                while (rs.next()) indexes[rs.getString(1)] = rs.getString(2)
            }
            assertTrue(indexes["idx_words_lang_text"].orEmpty().startsWith("CREATE UNIQUE INDEX"), "indexes: $indexes")
            assertTrue(indexes.keys.containsAll(setOf("idx_words_lang_status_text", "idx_words_lang_created")), "indexes: $indexes")

            // daily_eligible: NOT NULL with a false default, as the generated DDL declares it.
            var dailyEligible: Pair<String, String?>? = null
            exec(
                "SELECT is_nullable, column_default FROM information_schema.columns " +
                    "WHERE table_name = 'words' AND column_name = 'daily_eligible'"
            ) { rs -> if (rs.next()) dailyEligible = rs.getString(1) to rs.getString(2) }
            assertEquals("NO" to "false", dailyEligible)

            val old = WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq id }.single()
            assertEquals("older", old[WordSuggestionsTable.word])
            assertNull(old[WordSuggestionsTable.telegramChatId])
            assertNull(old[WordSuggestionsTable.telegramMessageId])
            assertNull(old[WordSuggestionsTable.telegramText])
            assertNull(old[WordSuggestionsTable.reviewReason])
            WordSuggestionsTable.deleteWhere { WordSuggestionsTable.id eq id }
        }

        transaction(db) { assertEquals(emptyList(), DatabaseFactory.pendingMigrationStatements()) }
    }

    @Test
    fun playerAdminColumnsAndIndexesAreAddedAndExistingAccountsReadBackUnblocked() {
        val db = DatabaseFactory.init()
        val id = java.util.UUID.randomUUID().toString()
        // A database from before player administration: no block columns, no search indexes, one existing account.
        transaction(db) {
            exec("DROP INDEX IF EXISTS idx_users_created_at")
            exec("DROP INDEX IF EXISTS idx_oauth_user")
            exec("ALTER TABLE users DROP COLUMN IF EXISTS suggestions_blocked_at, DROP COLUMN IF EXISTS suggestions_blocked_by")
            exec("INSERT INTO users (id, created_at, name) VALUES ('$id', now(), 'Grace')")
            db.dialectMetadata.resetCaches()
        }

        DatabaseFactory.migrate(db)

        transaction(db) {
            val columns = mutableMapOf<String, String>()
            exec(
                "SELECT column_name, is_nullable FROM information_schema.columns " +
                    "WHERE table_name = 'users' AND column_name IN ('suggestions_blocked_at', 'suggestions_blocked_by')"
            ) { rs -> while (rs.next()) columns[rs.getString(1)] = rs.getString(2) }
            assertEquals(mapOf("suggestions_blocked_at" to "YES", "suggestions_blocked_by" to "YES"), columns)

            val indexes = mutableMapOf<String, String>()
            exec("SELECT indexname, indexdef FROM pg_indexes WHERE tablename IN ('users', 'oauth_identities')") { rs ->
                while (rs.next()) indexes[rs.getString(1)] = rs.getString(2)
            }
            assertTrue(indexes["idx_users_created_at"].orEmpty().contains("(created_at)"), "indexes: $indexes")
            assertTrue(indexes["idx_oauth_user"].orEmpty().contains("(user_id)"), "indexes: $indexes")

            val existing = UsersTable.selectAll().where { UsersTable.id eq id }.single()
            assertEquals("Grace", existing[UsersTable.name])
            assertNull(existing[UsersTable.suggestionsBlockedAt])
            assertNull(existing[UsersTable.suggestionsBlockedBy])
            UsersTable.deleteWhere { UsersTable.id eq id }
        }

        // A second startup finds nothing left to do.
        transaction(db) { assertEquals(emptyList(), DatabaseFactory.pendingMigrationStatements()) }
    }

    @Test
    fun calendarTablesAreAddedToADatabaseMigratedByTheWordCatalogBuild() {
        val db = DatabaseFactory.init()
        // What the word-catalog build left behind: the catalog without the pool index and no calendar tables.
        transaction(db) {
            exec("DROP TABLE IF EXISTS lexeme_pairs, daily_words, calendar_notices, calendar_state CASCADE")
            exec("DROP INDEX IF EXISTS idx_words_lang_status_eligible")
            db.dialectMetadata.resetCaches()
        }

        DatabaseFactory.migrate(db)

        transaction(db) {
            assertTrue(LexemePairsTable.exists())
            assertTrue(DailyWordsTable.exists())
            assertTrue(CalendarNoticesTable.exists())
            assertTrue(CalendarStateTable.exists())

            val indexes = mutableMapOf<String, String>()
            exec("SELECT indexname, indexdef FROM pg_indexes WHERE tablename IN ('words', 'lexeme_pairs', 'daily_words')") { rs ->
                while (rs.next()) indexes[rs.getString(1)] = rs.getString(2)
            }
            assertTrue(indexes.containsKey("idx_words_lang_status_eligible"), "indexes: $indexes")
            assertTrue(indexes["idx_lexeme_pairs_latn"].orEmpty().startsWith("CREATE UNIQUE INDEX"), "indexes: $indexes")
            assertTrue(indexes["idx_lexeme_pairs_cyrl"].orEmpty().startsWith("CREATE UNIQUE INDEX"), "indexes: $indexes")
            // One row per calendar and day.
            assertTrue(indexes.values.any { it.contains("daily_words") && it.contains("(calendar, day)") }, "indexes: $indexes")
        }

        transaction(db) { assertEquals(emptyList(), DatabaseFactory.pendingMigrationStatements()) }
    }

    @Test
    fun analyticsTablesAreAddedToADatabaseMigratedByTheEarlierBuildsAndOldIdentitiesHaveNoLinkTime() {
        val db = DatabaseFactory.init()
        val userId = java.util.UUID.randomUUID().toString()
        val identityId = java.util.UUID.randomUUID().toString()
        val rollups = AnalyticsRollupJob.ROLLUP_TABLES.joinToString(", ") { it.tableName }
        // What the player-accounts build left behind: no analytics tables, no link time, one linked account.
        transaction(db) {
            exec("DROP TABLE IF EXISTS game_results, analytics_meta, account_events_daily, analytics_rollup_days, $rollups CASCADE")
            exec("ALTER TABLE oauth_identities DROP COLUMN IF EXISTS linked_at")
            exec("INSERT INTO users (id, created_at) VALUES ('$userId', now())")
            exec("INSERT INTO oauth_identities (id, user_id, provider, provider_subject) VALUES ('$identityId', '$userId', 'GOOGLE', 'schema-test-$identityId')")
            db.dialectMetadata.resetCaches()
        }

        DatabaseFactory.migrate(db)

        transaction(db) {
            assertTrue(GameResultsTable.exists())
            assertTrue(AnalyticsMetaTable.exists())
            assertTrue(AccountEventsDailyTable.exists())
            assertTrue(AnalyticsRollupDaysTable.exists())
            AnalyticsRollupJob.ROLLUP_TABLES.forEach { assertTrue(it.exists(), "${it.tableName} exists") }

            val indexes = mutableMapOf<String, String>()
            exec("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'game_results'") { rs ->
                while (rs.next()) indexes[rs.getString(1)] = rs.getString(2)
            }
            assertTrue(indexes["idx_game_results_lang_day"].orEmpty().contains("(lang, puzzle_day)"), "indexes: $indexes")
            assertTrue(indexes["idx_game_results_day"].orEmpty().contains("(puzzle_day)"), "indexes: $indexes")
            assertTrue(indexes.values.any { it.contains("(user_id, lang, puzzle_day)") }, "primary key: $indexes")

            // Aggregates only: no rollup table can hold a player id.
            val rollupColumns = mutableListOf<Pair<String, String>>()
            exec(
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_name IN " +
                    AnalyticsRollupJob.ROLLUP_TABLES.joinToString(", ", "(", ")") { "'${it.tableName}'" }
            ) { rs -> while (rs.next()) rollupColumns += rs.getString(1) to rs.getString(2) }
            assertEquals(AnalyticsRollupJob.ROLLUP_TABLES.map { it.tableName }.toSet(), rollupColumns.map { it.first }.toSet())
            assertTrue(rollupColumns.none { it.second == "user_id" }, "rollup columns: $rollupColumns")

            val identity = OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.id eq identityId }.single()
            assertNull(identity[OAuthIdentitiesTable.linkedAt])
        }

        // Deleting an account removes its game results.
        transaction(db) {
            GameResultsTable.insert {
                it[GameResultsTable.userId] = userId
                it[lang] = "en"
                it[puzzleDay] = 20_000L
                it[won] = true
                it[attempts] = 3
                it[receivedAt] = java.time.Instant.now()
            }
        }
        transaction(db) {
            UsersTable.deleteWhere { UsersTable.id eq userId }
            assertEquals(0, GameResultsTable.selectAll().where { GameResultsTable.userId eq userId }.count())
            assertEquals(0, OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.id eq identityId }.count())
        }

        transaction(db) { assertEquals(emptyList(), DatabaseFactory.pendingMigrationStatements()) }
    }
}
