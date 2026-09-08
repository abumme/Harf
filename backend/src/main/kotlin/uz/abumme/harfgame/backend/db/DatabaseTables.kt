package uz.abumme.harfgame.backend.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val createdAt = timestamp("created_at")
    /** User-confirmed display name captured at link time; null for anonymous / unnamed accounts. */
    val name = varchar("name", 255).nullable()

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

object WordSuggestionsTable : Table("word_suggestions") {
    val id = varchar("id", 36)
    val lang = varchar("lang", 16)
    val word = varchar("word", 64)
    /** Author account; SET NULL on deletion so contribution history outlives the account. */
    val suggestedBy = varchar("suggested_by", 36)
        .references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    /** Telegram editor who decided (id/username); null while pending. */
    val decidedBy = varchar("decided_by", 64).nullable()
    val decidedAt = timestamp("decided_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_suggestion_lang_word_status", false, lang, word, status)
        index("idx_suggestion_author_created", false, suggestedBy, createdAt)
    }
}

object WordPacksTable : Table("word_packs") {
    val lang = varchar("lang", 16)
    val version = varchar("version", 64)
    val effectiveFrom = long("effective_from")
    val anchorEpochDay = long("anchor_epoch_day")
    val answers = text("answers")   // JSON array of raw words
    val guesses = text("guesses")   // JSON array of raw words
    val schedule = text("schedule") // JSON array of raw words, indexed from anchorEpochDay
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(lang)
}
