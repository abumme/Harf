package uz.abumme.harfgame.backend.admin.players

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.resetAdminData
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.RefreshTokensTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Instant
import java.util.UUID

// Player-account fixtures for the player admin tests.

/** [resetAdminData] plus no player accounts at all (identities, tokens and stats go with them). */
fun resetPlayerData() {
    resetAdminData()
    transaction(DatabaseFactory.init()) { UsersTable.deleteAll() }
}

/** Inserts a player account directly and returns its id; [identities] are provider to subject. */
fun insertPlayer(
    name: String? = null,
    createdAt: Instant = Instant.parse("2026-09-01T08:00:00Z"),
    identities: List<Pair<OAuthProvider, String>> = emptyList(),
    blockedAt: Instant? = null,
    blockedBy: String? = null,
    id: String = UUID.randomUUID().toString(),
): String {
    transaction(DatabaseFactory.init()) {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.createdAt] = createdAt
            it[UsersTable.name] = name
            it[suggestionsBlockedAt] = blockedAt
            it[suggestionsBlockedBy] = blockedBy
        }
        for ((provider, subject) in identities) {
            OAuthIdentitiesTable.insert {
                it[OAuthIdentitiesTable.id] = UUID.randomUUID().toString()
                it[userId] = id
                it[OAuthIdentitiesTable.provider] = provider.name
                it[providerSubject] = subject
            }
        }
    }
    return id
}

/** Stores [records] as the account's accepted stats snapshot. */
fun insertStats(userId: String, records: List<ResultRecordDto>, updatedAt: Instant = Instant.parse("2026-09-15T12:00:00Z")) {
    transaction(DatabaseFactory.init()) {
        UserStatsTable.insert {
            it[UserStatsTable.userId] = userId
            it[data] = Json.encodeToString(records)
            it[UserStatsTable.updatedAt] = updatedAt
        }
    }
}

/** Inserts a refresh token row and returns its id; the hash is a stand-in, never a real token's. */
fun insertRefreshToken(
    userId: String,
    expiresAt: Instant,
    revokedAt: Instant? = null,
    replacedBy: String? = null,
    tokenHash: String = "hash-" + UUID.randomUUID().toString().replace("-", ""),
): String {
    val id = UUID.randomUUID().toString()
    transaction(DatabaseFactory.init()) {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[createdAt] = expiresAt.minusSeconds(30L * 24 * 3600)
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[RefreshTokensTable.revokedAt] = revokedAt
            it[RefreshTokensTable.replacedBy] = replacedBy
        }
    }
    return id
}

fun userRow(id: String) = transaction(DatabaseFactory.init()) {
    UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()
}
