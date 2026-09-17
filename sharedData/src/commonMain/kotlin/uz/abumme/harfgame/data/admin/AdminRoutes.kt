package uz.abumme.harfgame.data.admin

import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.api.ApiRoutes

/**
 * Paths, cookies and headers of the staff admin API, shared by the backend and the `:adminWeb` panel so both sides
 * compile against the same contract. Every admin route authenticates by staff session only (never a player token).
 */
object AdminRoutes {
    const val PREFIX = "${ApiRoutes.API_PREFIX}/admin"

    const val AUTH_LOGIN = "$PREFIX/auth/login"
    const val AUTH_LOGOUT = "$PREFIX/auth/logout"
    const val AUTH_ME = "$PREFIX/auth/me"
    const val AUTH_PASSWORD = "$PREFIX/auth/password"

    const val STAFF = "$PREFIX/staff"
    fun staff(id: String) = "$STAFF/$id"
    fun staffPassword(id: String) = "$STAFF/$id/password"
    fun staffDisable(id: String) = "$STAFF/$id/disable"
    fun staffEnable(id: String) = "$STAFF/$id/enable"

    const val AUDIT = "$PREFIX/audit"
    const val LANGUAGES = "$PREFIX/languages"

    const val WORDS = "$PREFIX/words"
    const val WORDS_CHECK = "$WORDS/check"
    const val WORDS_BULK = "$WORDS/bulk"
    fun word(id: String) = "$WORDS/$id"
    fun wordRemove(id: String) = "$WORDS/$id/remove"
    fun wordRestore(id: String) = "$WORDS/$id/restore"

    const val SUGGESTIONS = "$PREFIX/suggestions"
    fun suggestionDecision(id: String) = "$SUGGESTIONS/$id/decision"

    /** A calendar's answer pool (`en`, `ru`, `kk`, `uz`); Uzbek eligibility is managed through pairs. */
    const val ANSWER_POOL = "$PREFIX/answer-pool"
    fun answerPool(calendar: String) = "$ANSWER_POOL/$calendar"
    fun answerPoolCandidates(calendar: String) = "$ANSWER_POOL/$calendar/candidates"
    fun answerPoolWords(calendar: String) = "$ANSWER_POOL/$calendar/words"
    fun answerPoolWord(calendar: String, wordId: String) = "$ANSWER_POOL/$calendar/words/$wordId"
    const val ANSWER_POOL_UZ_CYRL_STATUS = "$ANSWER_POOL/uz/cyrl-status"
    const val ANSWER_POOL_UZ_PAIRS = "$ANSWER_POOL/uz/pairs"
    fun answerPoolUzPair(pairId: String) = "$ANSWER_POOL_UZ_PAIRS/$pairId"

    /** The daily-word calendars; a day is an ISO date in the calendar's timezone. */
    const val CALENDAR = "$PREFIX/calendar"
    const val CALENDAR_NOTICES = "$CALENDAR/notices"
    fun calendarNoticeDismiss(id: String) = "$CALENDAR_NOTICES/$id/dismiss"
    fun calendar(calendar: String) = "$CALENDAR/$calendar"
    fun calendarCandidates(calendar: String) = "$CALENDAR/$calendar/candidates"
    fun calendarDay(calendar: String, day: String) = "$CALENDAR/$calendar/days/$day"

    /** Player accounts (ADMIN): search, detail and the support actions on one account. */
    const val PLAYERS = "$PREFIX/players"
    fun player(id: String) = "$PLAYERS/$id"
    fun playerDelete(id: String) = "$PLAYERS/$id/delete"
    fun playerEndSessions(id: String) = "$PLAYERS/$id/end-sessions"
    fun playerSuggestionBlock(id: String) = "$PLAYERS/$id/suggestion-block"
    fun playerDisplayName(id: String) = "$PLAYERS/$id/display-name"

    /** Aggregated analytics (ADMIN): the overview tiles and one JSON endpoint plus one CSV export per table. */
    const val ANALYTICS = "$PREFIX/analytics"
    const val ANALYTICS_OVERVIEW = "$ANALYTICS/overview"
    fun analytics(table: AnalyticsTable) = "$ANALYTICS/${table.path}"
    fun analyticsCsv(table: AnalyticsTable) = "$ANALYTICS/${table.path}.csv"

    /** HttpOnly cookie holding the opaque session token. */
    const val SESSION_COOKIE = "harf_admin_session"

    /** Script-readable cookie with the session-bound anti-forgery token... */
    const val XSRF_COOKIE = "XSRF-TOKEN"

    /** ...which every POST/PUT/PATCH/DELETE except login must echo in this header. */
    const val XSRF_HEADER = "X-XSRF-TOKEN"
}
