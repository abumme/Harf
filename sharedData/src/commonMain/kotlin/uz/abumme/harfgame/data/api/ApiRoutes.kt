package uz.abumme.harfgame.data.api

object ApiRoutes {
    const val API_PREFIX = "/api/v1"

    const val AUTH_ANONYMOUS = "$API_PREFIX/auth/anonymous"
    const val AUTH_LINK = "$API_PREFIX/auth/link"
    const val AUTH_REFRESH = "$API_PREFIX/auth/refresh"
    const val AUTH_LOGOUT = "$API_PREFIX/auth/logout"

    const val ACCOUNT = "$API_PREFIX/account"

    const val SYNC_STATS = "$API_PREFIX/sync/stats"
}
