package uz.abumme.harfgame.backend.admin.audit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.data.admin.audit.ActorKind
import java.time.Clock
import java.util.UUID

/** Who performed an audited action. */
sealed interface AuditActor {
    val kind: ActorKind
    val staffId: String?

    data class Staff(override val staffId: String) : AuditActor {
        override val kind get() = ActorKind.STAFF
    }

    /** The server itself, e.g. bootstrapping an ADMIN from configuration. */
    data object System : AuditActor {
        override val kind get() = ActorKind.SYSTEM
        override val staffId: String? get() = null
    }

    /** A Telegram tap, attributed to the staff member whose Telegram user ID matched (if any). */
    data class Telegram(override val staffId: String?) : AuditActor {
        override val kind get() = ActorKind.TELEGRAM
    }
}

/**
 * Append-only audit writer. [record] must run inside the same `dbQuery` as the action it describes, so the entry
 * and the change commit or roll back together: a refused or failed action never leaves an entry claiming success.
 */
class AuditLog(private val clock: Clock) {

    fun record(
        actor: AuditActor,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        lang: String? = null,
        details: JsonObject? = null,
    ) {
        val stored = details?.let(::redact)?.takeIf { it.isNotEmpty() }
        StaffAuditLogTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[at] = clock.instant()
            it[actorKind] = actor.kind.name
            it[actorStaffId] = actor.staffId
            it[StaffAuditLogTable.action] = action
            it[StaffAuditLogTable.targetType] = targetType
            it[StaffAuditLogTable.targetId] = targetId
            it[StaffAuditLogTable.lang] = lang
            it[StaffAuditLogTable.details] = stored?.toString()
        }
    }

    companion object {
        private val secretKey = Regex("password|token|hash|secret", RegexOption.IGNORE_CASE)

        /** Drops every key named like a secret, at any depth: a second line of defence behind the callers. */
        fun redact(details: JsonObject): JsonObject =
            JsonObject(details.filterKeys { !secretKey.containsMatchIn(it) }.mapValues { (_, value) -> redactElement(value) })

        private fun redactElement(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> redact(element)
            is JsonArray -> JsonArray(element.map(::redactElement))
            else -> element
        }
    }
}

/** Builds audit `details`: changed fields as `{"field": {"from": …, "to": …}}` plus plain facts. */
class AuditDetails {
    private val entries = LinkedHashMap<String, JsonElement>()

    fun change(field: String, from: JsonElement, to: JsonElement) {
        entries[field] = JsonObject(mapOf("from" to from, "to" to to))
    }

    fun changeIfDifferent(field: String, from: JsonElement, to: JsonElement) {
        if (from != to) change(field, from, to)
    }

    fun fact(field: String, value: JsonElement) {
        entries[field] = value
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    fun build(): JsonObject = JsonObject(entries)
}

fun auditDetails(block: AuditDetails.() -> Unit): JsonObject = AuditDetails().apply(block).build()

fun jsonOf(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun jsonOf(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun jsonOf(values: Collection<String>): JsonElement = JsonArray(values.sorted().map(::JsonPrimitive))
