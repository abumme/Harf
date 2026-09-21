package uz.abumme.harfgame.data.admin.audit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ActorKind { STAFF, SYSTEM, TELEGRAM }

/**
 * One append-only audit entry. [at] is epoch milliseconds (UTC). [actorUsername] and [targetLabel] are resolved for
 * display when the actor or target is a staff account. [details] is a small object, usually
 * `{"field": {"from": …, "to": …}}`, and never holds secrets.
 */
@Serializable
data class AuditEntryDto(
    val id: String,
    val at: Long,
    val actorKind: ActorKind,
    val actorStaffId: String? = null,
    val actorUsername: String? = null,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val lang: String? = null,
    val details: JsonObject? = null,
)

/**
 * Query parameters of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.AUDIT]: [ACTOR] is a staff id, [FROM] and
 * [TO] are epoch milliseconds ([TO] exclusive), [PAGE] is zero-based.
 */
object AuditParams {
    const val ACTOR = "actor"
    const val ACTION = "action"
    const val LANG = "lang"
    const val FROM = "from"
    const val TO = "to"
    const val PAGE = "page"
    const val SIZE = "size"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200
}

/** Audit action names: UPPER_SNAKE, prefixed by area. Later changes add `WORD_*`, `SUGGESTION_*`, `DAILY_*`, `PLAYER_*`. */
object AuditActions {
    const val AUTH_LOGIN_SUCCEEDED = "AUTH_LOGIN_SUCCEEDED"
    const val AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED"
    const val AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED"
    const val AUTH_LOGGED_OUT = "AUTH_LOGGED_OUT"
    const val AUTH_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED"
    const val STAFF_CREATED = "STAFF_CREATED"
    const val STAFF_UPDATED = "STAFF_UPDATED"
    const val STAFF_PASSWORD_RESET = "STAFF_PASSWORD_RESET"
    const val STAFF_DISABLED = "STAFF_DISABLED"
    const val STAFF_ENABLED = "STAFF_ENABLED"
    const val STAFF_BOOTSTRAPPED = "STAFF_BOOTSTRAPPED"

    /** A word entered the catalog (staff add, accepted suggestion). */
    const val WORD_ADDED = "WORD_ADDED"

    /** A removed word became active again (restore, add of a removed spelling, accepted suggestion). */
    const val WORD_RESTORED = "WORD_RESTORED"

    /** A word was respelled; details carry the previous and new text. */
    const val WORD_EDITED = "WORD_EDITED"
    const val WORD_REMOVED = "WORD_REMOVED"

    /** SYSTEM, once per language: the published pack was carried into the catalog. */
    const val WORD_CATALOG_IMPORTED = "WORD_CATALOG_IMPORTED"

    /** SYSTEM, per language at startup: words of a deployed dictionary were added to the catalog. */
    const val WORD_BUNDLED_MERGED = "WORD_BUNDLED_MERGED"

    /** A suggestion was accepted or rejected (panel, Telegram, or automatic verification). */
    const val SUGGESTION_DECIDED = "SUGGESTION_DECIDED"

    /** Daily-word actions start with this prefix; members without calendar access never see them. */
    const val DAILY_PREFIX = "DAILY_"

    /** A word joined a calendar's answer pool. */
    const val DAILY_ELIGIBILITY_MARKED = "DAILY_ELIGIBILITY_MARKED"
    const val DAILY_ELIGIBILITY_UNMARKED = "DAILY_ELIGIBILITY_UNMARKED"

    /** An Uzbek lexeme pair was created (it is the eligibility of both words) or removed. */
    const val DAILY_PAIR_CREATED = "DAILY_PAIR_CREATED"
    const val DAILY_PAIR_REMOVED = "DAILY_PAIR_REMOVED"

    /** An ADMIN picked or unpicked the word of a calendar day. */
    const val DAILY_WORD_PICKED = "DAILY_WORD_PICKED"
    const val DAILY_WORD_UNPICKED = "DAILY_WORD_UNPICKED"
    const val DAILY_NOTICE_DISMISSED = "DAILY_NOTICE_DISMISSED"

    /** SYSTEM: a future manual pick lost its word (removed or no longer eligible) and the day was filled automatically. */
    const val DAILY_MANUAL_PICK_REPLACED = "DAILY_MANUAL_PICK_REPLACED"

    /** Player-account actions start with this prefix; only members who work on player accounts filter by them. */
    const val PLAYER_PREFIX = "PLAYER_"

    /** An ADMIN deleted a player account; details list only its provider types (`providers`). */
    const val PLAYER_DELETED = "PLAYER_DELETED"

    /** An ADMIN revoked every refresh token of a player account; details carry `revoked`. */
    const val PLAYER_SESSIONS_ENDED = "PLAYER_SESSIONS_ENDED"
    const val PLAYER_SUGGESTIONS_BLOCKED = "PLAYER_SUGGESTIONS_BLOCKED"
    const val PLAYER_SUGGESTIONS_UNBLOCKED = "PLAYER_SUGGESTIONS_UNBLOCKED"

    /** An ADMIN cleared a player's display name; the removed name is never kept. */
    const val PLAYER_DISPLAY_NAME_CLEARED = "PLAYER_DISPLAY_NAME_CLEARED"

    val ALL: List<String> = listOf(
        AUTH_LOGIN_SUCCEEDED, AUTH_LOGIN_FAILED, AUTH_ACCOUNT_LOCKED, AUTH_LOGGED_OUT, AUTH_PASSWORD_CHANGED,
        STAFF_CREATED, STAFF_UPDATED, STAFF_PASSWORD_RESET, STAFF_DISABLED, STAFF_ENABLED, STAFF_BOOTSTRAPPED,
        WORD_ADDED, WORD_RESTORED, WORD_EDITED, WORD_REMOVED, WORD_CATALOG_IMPORTED, WORD_BUNDLED_MERGED,
        SUGGESTION_DECIDED,
        DAILY_ELIGIBILITY_MARKED, DAILY_ELIGIBILITY_UNMARKED, DAILY_PAIR_CREATED, DAILY_PAIR_REMOVED, DAILY_WORD_PICKED,
        DAILY_WORD_UNPICKED, DAILY_NOTICE_DISMISSED, DAILY_MANUAL_PICK_REPLACED,
        PLAYER_DELETED, PLAYER_SESSIONS_ENDED, PLAYER_SUGGESTIONS_BLOCKED, PLAYER_SUGGESTIONS_UNBLOCKED,
        PLAYER_DISPLAY_NAME_CLEARED,
    )
}

/** `AuditEntryDto.targetType` values; later changes add their own. */
object AuditTargets {
    const val STAFF = "STAFF"
    const val WORD = "WORD"
    const val SUGGESTION = "SUGGESTION"

    /** A calendar day, id `<calendar>:<yyyy-mm-dd>`. */
    const val DAILY_DAY = "DAILY_DAY"

    /** An Uzbek lexeme pair. */
    const val LEXEME_PAIR = "LEXEME_PAIR"
    const val CALENDAR_NOTICE = "CALENDAR_NOTICE"

    /** A player account, by account id (no label: a display name is personal data). */
    const val PLAYER = "PLAYER"
}
