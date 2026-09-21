package uz.abumme.harfgame.data.admin

import kotlinx.serialization.Serializable

/** A staff member's role. The role -> permission matrix lives on the server only. */
@Serializable
enum class Role { ADMIN, WORDER }

/**
 * What a staff member may do in the admin API and panel. The server sends each member their effective set
 * (`MeDto.permissions`); the panel uses it only to decide what to show, the server checks every request.
 * Later changes add their own entries.
 */
@Serializable
enum class Permission {
    /** List, create, edit, reset passwords of, disable and enable staff accounts. */
    STAFF_MANAGE,

    /** Read every audit entry. */
    AUDIT_READ_ALL,

    /** Read only the entries where the member is the actor. */
    AUDIT_READ_OWN,

    /** See the own account and change the own password. */
    ACCOUNT_SELF,

    /** View and search catalog words (language-scoped). */
    WORDS_READ,

    /** Add, edit, remove and restore catalog words (language-scoped). */
    WORDS_WRITE,

    /** Review player word suggestions in the panel and in Telegram (language-scoped). */
    SUGGESTIONS_REVIEW,

    /** See and change the daily answer pool: eligible words, Uzbek pairs, the never-used counter. */
    DAILY_POOL_MANAGE,

    /** See the daily-word calendars, pick and unpick days, dismiss calendar notices. */
    CALENDAR_MANAGE,

    /** Search player accounts and see a player's detail (stats, suggestions, sessions, block). */
    PLAYERS_READ,

    /** Delete a player account, end its sessions, block or unblock its suggestions, clear its display name. */
    PLAYERS_WRITE,

    /** See aggregated analytics: the dashboard and its CSV exports. */
    ANALYTICS_READ,
}

/** Username, password and display-name rules, shared so the panel can check input before the server, which stays the authority. */
object StaffRules {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    const val PASSWORD_MIN = 12
    const val PASSWORD_MAX = 128
    const val DISPLAY_NAME_MAX = 64

    private val usernamePattern = Regex("^[a-z0-9._-]{$USERNAME_MIN,$USERNAME_MAX}$")

    /** Usernames are stored lowercase and compared ignoring case. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun isValidUsername(normalized: String): Boolean = usernamePattern.matches(normalized)

    fun isValidPassword(password: String): Boolean = password.length in PASSWORD_MIN..PASSWORD_MAX
}
