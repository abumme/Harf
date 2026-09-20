package uz.abumme.harfgame.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

object DatabaseFactory {
    @Volatile
    private var database: Database? = null
    private var dataSource: HikariDataSource? = null

    @Synchronized
    fun init(
        jdbcUrl: String = System.getenv("DB_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/harf",
        user: String = System.getenv("DB_USER") ?: "harf",
        pass: String = System.getenv("DB_PASSWORD") ?: "harf_password",
        maxPoolSize: Int = (System.getenv("DB_MAX_POOL_SIZE") ?: "10").toInt(),
    ): Database {
        val existing = database
        if (existing != null) {
            return existing
        }

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            username = user
            password = pass
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            // pgjdbc 42.7.13 merges up to 32768 rows per round-trip when batched inserts are
            // rewritten into a single multi-row INSERT — speeds the bundled word-pack seed.
            addDataSourceProperty("reWriteBatchedInserts", "true")
            validate()
        }
        val ds = HikariDataSource(config)
        dataSource = ds
        val db = Database.connect(ds)
        database = db

        transaction(db) {
            // Bring the schema in line with the table definitions. Postgres runs DDL
            // transactionally, so applying the whole diff in one transaction is atomic — any failure
            // rolls back, which is exactly the hazard the deprecated createMissingTablesAndColumns had.
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                UsersTable,
                OAuthIdentitiesTable,
                RefreshTokensTable,
                UserStatsTable,
                WordPacksTable,
                WordSuggestionsTable,
                SuggestionReportsTable,
                withLogs = false,
            )
            // MigrationUtils can emit destructive DROPs for columns/tables absent from the model
            // (e.g. after a rename). Refuse to auto-run those — apply them by hand after review.
            // ponytail: schema-diff, not versioned. Adopt Flyway when migrations need review,
            // data backfills, or the destructive changes this guard blocks.
            val destructive = statements.filter { it.trimStart().uppercase().startsWith("DROP") || it.uppercase().contains("DROP COLUMN") }
            require(destructive.isEmpty()) { "Refusing destructive migration; apply manually: $destructive" }
            statements.forEach { exec(it) }
            // Raw exec() skips the cache reset SchemaUtils does after DDL, so the table names cached
            // while diffing (none, on a fresh database) would outlive the CREATEs and make
            // Table.exists() report false for tables that now exist.
            db.dialectMetadata.resetCaches()
        }
        return db
    }

    /** Runs [block] in a transaction; [transactionIsolation] (a `java.sql.Connection` level) overrides the pool default. */
    suspend fun <T> dbQuery(transactionIsolation: Int? = null, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction(db = database, transactionIsolation = transactionIsolation) { block() }
        }
}
