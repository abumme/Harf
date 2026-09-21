package uz.abumme.harfgame.admin.session

import uz.abumme.harfgame.admin.api.encodeQueryComponent
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.audit.AuditActions

/** Panel routes, relative to the site's base path (`/harf/admin`). */
object Routes {
    const val HOME = "/"
    const val LOGIN = "/login"
    const val STAFF = "/staff"
    const val STAFF_NEW = "/staff/new"
    const val STAFF_EDIT = "/staff/edit"
    const val AUDIT = "/audit"
    const val ACCOUNT = "/account"
    const val FORBIDDEN = "/forbidden"
    const val WORDS = "/words"
    const val SUGGESTIONS = "/suggestions"
    const val CALENDAR = "/calendar"
    const val CALENDAR_TABLE = "/calendar/table"
    const val ANSWER_POOL = "/answer-pool"
    const val PLAYERS = "/players"
    const val ANALYTICS = "/analytics"

    /** Set on the players list after a deletion, so it can confirm it once. */
    const val PLAYER_DELETED_PARAM = "deleted"

    fun staffEdit(id: String) = "$STAFF_EDIT?id=${encodeQueryComponent(id)}"

    /** A player's detail: a dynamic route, not exported; a direct load is served `index.html` and routed client-side. */
    fun player(id: String) = "$PLAYERS/${encodeQueryComponent(id)}"

    /** The login page, returning to [next] after sign-in when it is a panel page worth returning to. */
    fun login(next: String? = null): String {
        val target = next?.takeIf { isSafeNext(it) && it != HOME }
        return if (target == null) LOGIN else "$LOGIN?next=${encodeQueryComponent(target)}"
    }

    /** Where to go after sign-in: [next] if it is an internal panel route, otherwise [home] (the member's home page). */
    fun afterLogin(next: String?, home: String = HOME): String = next?.takeIf(::isSafeNext) ?: home

    /** Only same-site panel paths: no scheme, no protocol-relative `//host`, no looping back to login. */
    private fun isSafeNext(next: String): Boolean =
        next.startsWith("/") && !next.startsWith("//") && !next.contains("\\") && !next.contains("://") &&
            next.substringBefore('?') != LOGIN
}

/** The sections of the panel navigation. Later changes add theirs. */
enum class NavSection(val route: String) {
    /** Aggregated analytics: the ADMIN home. */
    ANALYTICS(Routes.ANALYTICS),

    /** The word catalog; also the WORDER's home. */
    WORDS(Routes.WORDS),

    /** Player word suggestions awaiting review. */
    SUGGESTIONS(Routes.SUGGESTIONS),

    /** The daily-word calendars (ADMIN only). */
    CALENDAR(Routes.CALENDAR),

    /** The words daily words are picked from (ADMIN only). */
    ANSWER_POOL(Routes.ANSWER_POOL),

    /** Player accounts (ADMIN only). */
    PLAYERS(Routes.PLAYERS),
    STAFF(Routes.STAFF),

    /** Every staff member's audit entries. */
    AUDIT_LOG(Routes.AUDIT),

    /** The member's own audit entries, on the same page. */
    ACTIVITY(Routes.AUDIT),
    ACCOUNT(Routes.ACCOUNT),
}

/**
 * Navigation entries, a pure function of the member's effective permissions (the server decides those; this only
 * decides what to show). Analytics comes first and is the home of whoever may read it (see [homeRedirect]).
 */
fun navigationFor(permissions: Set<Permission>): List<NavSection> = buildList {
    if (Permission.ANALYTICS_READ in permissions) add(NavSection.ANALYTICS)
    if (Permission.WORDS_READ in permissions) add(NavSection.WORDS)
    if (Permission.SUGGESTIONS_REVIEW in permissions) add(NavSection.SUGGESTIONS)
    if (Permission.CALENDAR_MANAGE in permissions) add(NavSection.CALENDAR)
    if (Permission.DAILY_POOL_MANAGE in permissions) add(NavSection.ANSWER_POOL)
    if (Permission.PLAYERS_READ in permissions) add(NavSection.PLAYERS)
    if (Permission.STAFF_MANAGE in permissions) add(NavSection.STAFF)
    when {
        Permission.AUDIT_READ_ALL in permissions -> add(NavSection.AUDIT_LOG)
        Permission.AUDIT_READ_OWN in permissions -> add(NavSection.ACTIVITY)
    }
    if (Permission.ACCOUNT_SELF in permissions) add(NavSection.ACCOUNT)
}

/** What each page requires: at least one of the listed permissions (an empty set means any signed-in member). */
object PageAccess {
    val HOME: Set<Permission> = emptySet()
    val STAFF: Set<Permission> = setOf(Permission.STAFF_MANAGE)
    val AUDIT: Set<Permission> = setOf(Permission.AUDIT_READ_ALL, Permission.AUDIT_READ_OWN)
    val ACCOUNT: Set<Permission> = setOf(Permission.ACCOUNT_SELF)
    val WORDS: Set<Permission> = setOf(Permission.WORDS_READ)
    val SUGGESTIONS: Set<Permission> = setOf(Permission.SUGGESTIONS_REVIEW)
    val CALENDAR: Set<Permission> = setOf(Permission.CALENDAR_MANAGE)
    val ANSWER_POOL: Set<Permission> = setOf(Permission.DAILY_POOL_MANAGE)

    /** The players list and a player's detail; the actions on it also need `PLAYERS_WRITE`. */
    val PLAYERS: Set<Permission> = setOf(Permission.PLAYERS_READ)

    /** The analytics dashboard (ADMIN). */
    val ANALYTICS: Set<Permission> = setOf(Permission.ANALYTICS_READ)

    fun allows(required: Set<Permission>, permissions: Set<Permission>): Boolean =
        required.isEmpty() || required.any { it in permissions }
}

/**
 * The audit actions a member can filter by: daily-word actions only for members who work on the calendar or the answer
 * pool (the server never returns them to anyone else), player-account actions only for members who work on players.
 */
fun auditActionsFor(permissions: Set<Permission>): List<String> {
    val daily = Permission.CALENDAR_MANAGE in permissions || Permission.DAILY_POOL_MANAGE in permissions
    val players = Permission.PLAYERS_READ in permissions
    return AuditActions.ALL.filter {
        (daily || !it.startsWith(AuditActions.DAILY_PREFIX)) && (players || !it.startsWith(AuditActions.PLAYER_PREFIX))
    }
}

/**
 * A member's home page, where sign-in and `/` send them: the analytics dashboard for whoever may read it (an ADMIN), the
 * word list for whoever works on words (a WORDER), the own account otherwise; null for a member who may open nothing.
 */
fun homeRedirect(permissions: Set<Permission>): String? = when {
    Permission.ANALYTICS_READ in permissions -> Routes.ANALYTICS
    Permission.WORDS_READ in permissions -> Routes.WORDS
    Permission.ACCOUNT_SELF in permissions -> Routes.ACCOUNT
    else -> null
}
