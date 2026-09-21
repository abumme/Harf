package uz.abumme.harfgame.backend.admin.analytics

import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import org.jetbrains.exposed.v1.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

/**
 * Plain SQL for the aggregates Exposed's DSL does not express well (FILTER clauses, window functions, ordered-set
 * aggregates). Postgres only, like production and the tests.
 *
 * Parameters are named `:name` and bound through Exposed's own column types, so a timestamp compares exactly as the
 * DSL wrote it (Exposed stores `timestamp` columns as wall-clock time in the JVM's zone). A list value expands to one
 * placeholder per element, for `IN (:names)`. Runs inside the caller's transaction.
 */
internal object AnalyticsSql {
    private val namedParameter = Regex("""(?<!:):([A-Za-z_][A-Za-z0-9_]*)""")
    private val instantType = JavaInstantColumnType()
    private val dateType = JavaLocalDateColumnType()

    fun <T> query(sql: String, params: Map<String, Any?> = emptyMap(), row: (ResultSet) -> T): List<T> {
        val (statement, args) = bind(sql, params)
        return TransactionManager.current().exec(statement, args, StatementType.SELECT) { rs ->
            val rows = ArrayList<T>()
            while (rs.next()) rows += row(rs)
            rows
        } ?: emptyList()
    }

    fun update(sql: String, params: Map<String, Any?> = emptyMap(), type: StatementType = StatementType.UPDATE) {
        val (statement, args) = bind(sql, params)
        TransactionManager.current().exec(statement, args, type)
    }

    /** Caps every statement of the current transaction, so a runaway analytics query cannot hold the small box. */
    fun limitStatementTime() {
        TransactionManager.current().exec("SET LOCAL statement_timeout = '$STATEMENT_TIMEOUT'", emptyList(), StatementType.OTHER)
    }

    private fun bind(sql: String, params: Map<String, Any?>): Pair<String, List<Pair<IColumnType<*>, Any?>>> {
        val args = ArrayList<Pair<IColumnType<*>, Any?>>()
        val statement = namedParameter.replace(sql) { match ->
            val name = match.groupValues[1]
            require(params.containsKey(name)) { "No value for :$name" }
            when (val value = params[name]) {
                is Collection<*> -> {
                    require(value.isNotEmpty()) { ":$name is an empty list" }
                    value.forEach { args += typed(it) }
                    value.joinToString(", ") { "?" }
                }
                else -> {
                    args += typed(value)
                    "?"
                }
            }
        }
        return statement to args
    }

    private fun typed(value: Any?): Pair<IColumnType<*>, Any?> = when (value) {
        is Instant -> instantType to value
        is LocalDate -> dateType to value
        is Long -> LongColumnType() to value
        is Int -> IntegerColumnType() to value
        is Double -> DoubleColumnType() to value
        is Boolean -> BooleanColumnType() to value
        is String -> TextColumnType() to value
        else -> throw IllegalArgumentException("Unsupported SQL parameter: $value")
    }

    private const val STATEMENT_TIMEOUT = "30s"
}

/** A nullable integer column of [rs]. */
internal fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

/** A nullable double column of [rs]. */
internal fun ResultSet.doubleOrNull(column: String): Double? = getDouble(column).takeUnless { wasNull() }

/** A nullable long column of [rs]. */
internal fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }
