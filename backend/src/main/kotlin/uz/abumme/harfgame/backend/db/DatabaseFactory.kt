package uz.abumme.harfgame.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

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
            validate()
        }
        val ds = HikariDataSource(config)
        dataSource = ds
        val db = Database.connect(ds)
        database = db

        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                OAuthIdentitiesTable,
                RefreshTokensTable,
                UserStatsTable,
                WordPacksTable,
            )
        }
        return db
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
}
