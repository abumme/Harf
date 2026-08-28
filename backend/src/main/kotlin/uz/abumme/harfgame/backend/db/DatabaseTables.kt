package uz.abumme.harfgame.backend.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object OAuthIdentitiesTable : Table("oauth_identities") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = varchar("provider", 32)
    val providerSubject = varchar("provider_subject", 255)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_oauth_provider_subject", provider, providerSubject)
    }
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 64).index("idx_refresh_token_hash")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val replacedBy = varchar("replaced_by", 36).references(id, onDelete = ReferenceOption.SET_NULL).nullable()
    val rotatedAt = timestamp("rotated_at").nullable()
    val graceReplacementToken = text("grace_replacement_token").nullable()

    override val primaryKey = PrimaryKey(id)
}

object UserStatsTable : Table("user_stats") {
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val data = text("data")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}
