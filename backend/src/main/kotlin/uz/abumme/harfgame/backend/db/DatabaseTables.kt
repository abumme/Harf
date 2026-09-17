package uz.abumme.harfgame.backend.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val createdAt = timestamp("created_at")
    /** User-confirmed display name captured at link time; null for anonymous / unnamed accounts. */
    val name = varchar("name", 255).nullable()
    /** When an ADMIN blocked this account's word suggestions; null while it may suggest. */
    val suggestionsBlockedAt = timestamp("suggestions_blocked_at").nullable()
    /** The staff id of the ADMIN who blocked it (no foreign key: staff are never deleted). */
    val suggestionsBlockedBy = varchar("suggestions_blocked_by", 36).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        // The staff panel's player search orders and filters by creation time.
        index("idx_users_created_at", false, createdAt)
    }
}

object OAuthIdentitiesTable : Table("oauth_identities") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = varchar("provider", 32)
    val providerSubject = varchar("provider_subject", 255)
    /** When the identity was linked; NULL for links made before link times were recorded (analytics). */
    val linkedAt = timestamp("linked_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_oauth_provider_subject", provider, providerSubject)
        // An account's identities: player search type filters, provider lists and the account-deletion cascade.
        index("idx_oauth_user", false, userId)
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
    /** Who decided: the Telegram editor's id, or "wiktionary" for an automatic acceptance; null while pending. */
    val decidedBy = varchar("decided_by", 64).nullable()
    val decidedAt = timestamp("decided_at").nullable()
    /**
     * Background review progress: QUEUED until Telegram confirms the suggestion's message, then POSTED.
     * NULL on rows that predate the review worker (they were posted inline), so the worker never selects them.
     */
    val reviewState = varchar("review_state", 16).nullable()
    /** Failed dictionary lookups so far; NULL counts as zero. */
    val lookupAttempts = integer("lookup_attempts").nullable()
    /** AUTO (dictionary verification) or EDITOR; NULL on decisions made before automatic acceptance existed. */
    val decidedVia = varchar("decided_via", 16).nullable()
    /** Word form (DICTIONARY | INFLECTED) found by an automatic acceptance, so a retried announcement can name it. */
    val autoForm = varchar("auto_form", 16).nullable()
    /**
     * The editors' Telegram decision message, recorded once Telegram confirmed it, so a panel decision can replace its
     * controls with the outcome. [telegramText] is the exact posted text (Telegram cannot append to a message). Cleared
     * once a decision edit succeeded; NULL for messages posted before this was recorded.
     */
    val telegramChatId = varchar("telegram_chat_id", 64).nullable()
    val telegramMessageId = long("telegram_message_id").nullable()
    val telegramText = text("telegram_text").nullable()
    /** Why the suggestion went to editors (a ReviewReason name); NULL while queued and on rows reviewed earlier. */
    val reviewReason = varchar("review_reason", 16).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_suggestion_lang_word_status", false, lang, word, status)
        index("idx_suggestion_author_created", false, suggestedBy, createdAt)
        index("idx_suggestion_review_state", false, reviewState)
    }
}

/** One row per language and Asia/Tashkent day whose report was delivered (or skipped as quiet), so it is sent once. */
object SuggestionReportsTable : Table("suggestion_reports") {
    val lang = varchar("lang", 16)
    val day = date("day")
    val sentAt = timestamp("sent_at")

    override val primaryKey = PrimaryKey(lang, day)
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

/**
 * Staff accounts of the admin panel (ADMIN, WORDER), kept apart from players. Never deleted, only DISABLED, so audit
 * attribution survives. [username] is stored lowercase; [failedLoginCount] and [lockedUntil] implement the sign-in
 * lockout.
 */
object StaffTable : Table("staff") {
    val id = varchar("id", 36)
    val username = varchar("username", 32).uniqueIndex("idx_staff_username")
    val displayName = varchar("display_name", 64).nullable()
    val role = varchar("role", 16)
    val status = varchar("status", 16)
    /** Argon2id PHC string. */
    val passwordHash = text("password_hash")
    val passwordChangedAt = timestamp("password_changed_at")
    val failedLoginCount = integer("failed_login_count")
    val lockedUntil = timestamp("locked_until").nullable()
    /** Used for Telegram review authorization by `word-catalog`. */
    val telegramUserId = long("telegram_user_id").nullable().uniqueIndex("idx_staff_telegram_user_id")
    val createdAt = timestamp("created_at")
    /** The ADMIN who created the account; null for a bootstrapped one. */
    val createdBy = varchar("created_by", 36).nullable()
    val updatedAt = timestamp("updated_at")
    val lastLoginAt = timestamp("last_login_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** A WORDER's assigned languages (pack language ids). Rows are kept when the account is promoted to ADMIN. */
object StaffLanguagesTable : Table("staff_languages") {
    val staffId = varchar("staff_id", 36).references(StaffTable.id, onDelete = ReferenceOption.CASCADE)
    val lang = varchar("lang", 16)

    override val primaryKey = PrimaryKey(staffId, lang)
}

/** Server-side staff sessions. Only the SHA-256 of the cookie token is stored. */
object StaffSessionsTable : Table("staff_sessions") {
    val id = varchar("id", 36)
    val staffId = varchar("staff_id", 36)
        .references(StaffTable.id, onDelete = ReferenceOption.CASCADE)
        .index("idx_staff_sessions_staff")
    val tokenHash = varchar("token_hash", 64).uniqueIndex("idx_staff_sessions_token_hash")
    val createdAt = timestamp("created_at")
    /** Written at most once a minute per session. */
    val lastSeenAt = timestamp("last_seen_at")
    /** Absolute end of the session, 7 days after sign-in. */
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val ip = varchar("ip", 64).nullable()
    val userAgent = varchar("user_agent", 256).nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Append-only record of staff sign-ins and actions; nothing updates or deletes rows. */
object StaffAuditLogTable : Table("staff_audit_log") {
    val id = varchar("id", 36)
    val at = timestamp("at")
    /** STAFF, SYSTEM or TELEGRAM. */
    val actorKind = varchar("actor_kind", 16)
    val actorStaffId = varchar("actor_staff_id", 36).nullable()
    val action = varchar("action", 48)
    val targetType = varchar("target_type", 24).nullable()
    val targetId = varchar("target_id", 64).nullable()
    val lang = varchar("lang", 16).nullable()
    /** Small JSON object, usually `{"field": {"from": …, "to": …}}`; never secrets. */
    val details = text("details").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_staff_audit_at", false, at)
        index("idx_staff_audit_actor_at", false, actorStaffId, at)
        index("idx_staff_audit_lang_at", false, lang, at)
    }
}

/**
 * The word catalog: one row per spelling per language, the source of truth the published `word_packs` row is rebuilt
 * from. [text] is the normalized form and unique per language across both statuses, so a REMOVED row is a tombstone
 * that keeps the spelling from being merged or auto-accepted back. [dailyEligible] marks the answer pool; this change
 * only carries it over from the packs and never exposes it.
 */
object WordsTable : Table("words") {
    val id = varchar("id", 36)
    val lang = varchar("lang", 16)
    val text = varchar("text", 64)
    /** ACTIVE or REMOVED. */
    val status = varchar("status", 16)
    /** BUNDLED, SUGGESTION, AUTO or STAFF. */
    val wordSource = varchar("source", 16)
    val suggestionId = varchar("suggestion_id", 36)
        .references(WordSuggestionsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val dailyEligible = bool("daily_eligible").default(false)
    val createdByStaffId = varchar("created_by_staff_id", 36).references(StaffTable.id).nullable()
    val createdAt = timestamp("created_at")
    val updatedByStaffId = varchar("updated_by_staff_id", 36).nullable()
    val updatedAt = timestamp("updated_at")
    val removedByStaffId = varchar("removed_by_staff_id", 36).nullable()
    val removedAt = timestamp("removed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_words_lang_text", lang, text)
        index("idx_words_lang_status_text", false, lang, status, text)
        index("idx_words_lang_created", false, lang, createdAt)
        // The answer pool of a language: every reconcile and publish reads it.
        index("idx_words_lang_status_eligible", false, lang, status, dailyEligible)
    }
}

/**
 * Uzbek daily eligibility: an Uzbek Latin and an Uzbek Cyrillic catalog word of the same lexeme. A word belongs to at
 * most one pair. Creating a pair sets `daily_eligible` on both words and removing it clears both, so the pack builder
 * reads one flag for every language.
 */
object LexemePairsTable : Table("lexeme_pairs") {
    val id = varchar("id", 36)
    val latnWordId = varchar("latn_word_id", 36)
        .references(WordsTable.id, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex("idx_lexeme_pairs_latn")
    val cyrlWordId = varchar("cyrl_word_id", 36)
        .references(WordsTable.id, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex("idx_lexeme_pairs_cyrl")
    val createdByStaffId = varchar("created_by_staff_id", 36).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * The daily-word calendar: one row per calendar (`en`, `ru`, `kk`, `uz`) and day in that calendar's timezone.
 * [text] (the Latin text for `uz`) and [textCyrl] are snapshots of what is published for the day, rewritten only while
 * the day is unlocked; [wordId] (`en`/`ru`/`kk`) and [lexemeId] (`uz`) point at what a MANUAL or AUTO pick was made
 * from, and are NULL for LEGACY days whose word is not in the catalog.
 */
object DailyWordsTable : Table("daily_words") {
    val calendar = varchar("calendar", 16)
    val day = date("day")
    val wordId = varchar("word_id", 36).nullable()
    val lexemeId = varchar("lexeme_id", 36).nullable()
    val text = varchar("text", 64)
    val textCyrl = varchar("text_cyrl", 64).nullable()
    /** MANUAL, AUTO or LEGACY. The column is `source` (a name `ColumnSet.source` already takes in Kotlin). */
    val daySource = varchar("source", 16)
    val isRepeat = bool("is_repeat")
    val pickedByStaffId = varchar("picked_by_staff_id", 36).nullable()
    val pickedAt = timestamp("picked_at")

    override val primaryKey = PrimaryKey(calendar, day)
}

/** What ADMINs are told about the calendar: that a future manual pick was replaced, and why. */
object CalendarNoticesTable : Table("calendar_notices") {
    val id = varchar("id", 36)
    val calendar = varchar("calendar", 16)
    val day = date("day")
    /** MANUAL_PICK_REPLACED. */
    val kind = varchar("kind", 32)
    val wordText = varchar("word_text", 64)
    /** REMOVED or INELIGIBLE. */
    val reason = varchar("reason", 16)
    val createdAt = timestamp("created_at")
    val dismissedAt = timestamp("dismissed_at").nullable()
    val dismissedByStaffId = varchar("dismissed_by_staff_id", 36).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_calendar_notices_created", false, createdAt)
    }
}

/** A calendar's first run: its history was imported, and [initializedOn] is the history start unless configured. */
object CalendarStateTable : Table("calendar_state") {
    val calendar = varchar("calendar", 16)
    val initializedOn = date("initialized_on")

    override val primaryKey = PrimaryKey(calendar)
}

// --- analytics ------------------------------------------------------------------------------------------------------

/**
 * One synced game result per account, language and puzzle day, recorded from stats uploads (and the one-time sweep of
 * stored snapshots). The first recorded result for a key wins and later snapshots never remove it. The only analytics
 * table that names an account; it goes with the account.
 */
object GameResultsTable : Table("game_results") {
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val lang = varchar("lang", 16)
    val puzzleDay = long("puzzle_day")
    val won = bool("won")
    val attempts = integer("attempts")
    /** When the server recorded it: the upload time, or the swept snapshot's `updated_at`. */
    val receivedAt = timestamp("received_at")

    override val primaryKey = PrimaryKey(userId, lang, puzzleDay)

    init {
        index("idx_game_results_lang_day", false, lang, puzzleDay)
        index("idx_game_results_day", false, puzzleDay)
    }
}

/** Small analytics bookkeeping values by key, e.g. the snapshot sweep's watermark. */
object AnalyticsMetaTable : Table("analytics_meta") {
    val key = varchar("key", 64)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}

/** Explicit account deletions per Asia/Tashkent date; no identity is kept. */
object AccountEventsDailyTable : Table("account_events_daily") {
    val date = date("date")
    val deletions = integer("deletions")

    override val primaryKey = PrimaryKey(date)
}

/**
 * Which rollup days exist: [grp] is `lang:<lang>`, `global`, `cohort`, `word:<calendar>` or `events`, [day] an epoch
 * day (the puzzle day, or the Asia/Tashkent date for `events`). A day is recomputed from raw data until it is [final].
 */
object AnalyticsRollupDaysTable : Table("analytics_rollup_days") {
    val grp = varchar("grp", 32)
    val day = long("day")
    val computedAt = timestamp("computed_at")
    val final = bool("final")

    override val primaryKey = PrimaryKey(grp, day)
}

// Rollup tables: aggregates only, never a player id; they survive account deletion.

/** Per language and puzzle day: results, outcomes and current-streak buckets. [players] equals [games] by key. */
object AnalyticsLangDayTable : Table("analytics_lang_day") {
    val lang = varchar("lang", 16)
    val puzzleDay = long("puzzle_day")
    val players = integer("players")
    val games = integer("games")
    val wins = integer("wins")
    val losses = integer("losses")
    val won1 = integer("won_1")
    val won2 = integer("won_2")
    val won3 = integer("won_3")
    val won4 = integer("won_4")
    val won5 = integer("won_5")
    val won6 = integer("won_6")
    val attemptsSumWon = long("attempts_sum_won")
    val streak1 = integer("streak_1")
    val streak2to6 = integer("streak_2_6")
    val streak7to29 = integer("streak_7_29")
    val streak30plus = integer("streak_30_plus")

    override val primaryKey = PrimaryKey(lang, puzzleDay)
}

/** Distinct accounts with a result in any language on the day, the 7 and the 30 days ending with it. */
object AnalyticsGlobalDayTable : Table("analytics_global_day") {
    val puzzleDay = long("puzzle_day")
    val dau = integer("dau")
    val wau = integer("wau")
    val mau = integer("mau")

    override val primaryKey = PrimaryKey(puzzleDay)
}

/** A cohort (earliest result on [cohortDay]) and its members with a result 1, 7 and 30 days later; null until known. */
object AnalyticsCohortDayTable : Table("analytics_cohort_day") {
    val cohortDay = long("cohort_day")
    val size = integer("size")
    val d1 = integer("d1").nullable()
    val d7 = integer("d7").nullable()
    val d30 = integer("d30").nullable()

    override val primaryKey = PrimaryKey(cohortDay)
}

/** A calendar's word of a puzzle day and how it was played (Uzbek: both scripts together). */
object AnalyticsWordDayTable : Table("analytics_word_day") {
    val calendar = varchar("calendar", 16)
    val puzzleDay = long("puzzle_day")
    val word = varchar("word", 64)
    val wordCyrl = varchar("word_cyrl", 64).nullable()
    /** MANUAL or AUTO; NULL when the calendar did not pick the day. The column is `source`. */
    val daySource = varchar("source", 16).nullable()
    val isRepeat = bool("is_repeat")
    val players = integer("players")
    val wins = integer("wins")
    val attemptsSumWon = long("attempts_sum_won")

    override val primaryKey = PrimaryKey(calendar, puzzleDay)
}

/** Account flows of an Asia/Tashkent date, and the end-of-day totals captured when the date was first computed. */
object AnalyticsAccountsDayTable : Table("analytics_accounts_day") {
    val date = date("date")
    val newAccounts = integer("new_accounts")
    val linksGoogle = integer("links_google").nullable()
    val linksApple = integer("links_apple").nullable()
    val deletions = integer("deletions")
    val total = integer("total").nullable()
    val linked = integer("linked").nullable()
    val googleLinked = integer("google_linked").nullable()
    val appleLinked = integer("apple_linked").nullable()

    override val primaryKey = PrimaryKey(date)
}

/** Suggestions of a language on an Asia/Tashkent date. */
object AnalyticsSuggestionsDayTable : Table("analytics_suggestions_day") {
    val date = date("date")
    val lang = varchar("lang", 16)
    val submitted = integer("submitted")
    val autoAccepted = integer("auto_accepted")
    val editorAccepted = integer("editor_accepted")
    val rejected = integer("rejected")
    val backlogEnd = integer("backlog_end")
    val medianAutoSeconds = double("median_auto_seconds").nullable()
    val medianEditorSeconds = double("median_editor_seconds").nullable()

    override val primaryKey = PrimaryKey(date, lang)
}

/** Catalog changes of a language on an Asia/Tashkent date; [activeWords] is captured once. */
object AnalyticsContentDayTable : Table("analytics_content_day") {
    val date = date("date")
    val lang = varchar("lang", 16)
    val activeWords = integer("active_words").nullable()
    val addedBundled = integer("added_bundled")
    val addedSuggestion = integer("added_suggestion")
    val addedAuto = integer("added_auto")
    val addedStaff = integer("added_staff")
    val removed = integer("removed")
    val restored = integer("restored")

    override val primaryKey = PrimaryKey(date, lang)
}

/** A calendar's answer pool at the end of an Asia/Tashkent date, captured once. */
object AnalyticsPoolDayTable : Table("analytics_pool_day") {
    val date = date("date")
    val calendar = varchar("calendar", 16)
    val poolSize = integer("pool_size").nullable()
    val unusedLeft = integer("unused_left").nullable()

    override val primaryKey = PrimaryKey(date, calendar)
}

/** What a staff member (or unlinked Telegram editors, `telegram-unlinked`) did in a language on a date. */
object AnalyticsStaffDayTable : Table("analytics_staff_day") {
    val date = date("date")
    val staffKey = varchar("staff_key", 64)
    val lang = varchar("lang", 16)
    val added = integer("added")
    val edited = integer("edited")
    val removed = integer("removed")
    val decided = integer("decided")

    override val primaryKey = PrimaryKey(date, staffKey, lang)
}
