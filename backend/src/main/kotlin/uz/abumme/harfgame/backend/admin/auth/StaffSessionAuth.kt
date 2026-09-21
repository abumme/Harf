package uz.abumme.harfgame.backend.admin.auth

import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.response.respond
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.api.ApiErrorResponse

/** Name of the staff authentication provider; admin routes sit under `authenticate(STAFF_SESSION_AUTH)` only. */
const val STAFF_SESSION_AUTH = "staff-session"

/**
 * Authenticates admin requests by the `harf_admin_session` cookie alone, so a player access token is never
 * consulted there. Resolves the session to a [uz.abumme.harfgame.backend.admin.access.StaffPrincipal] or answers
 * `401 unauthorized`.
 */
class StaffSessionAuthenticationProvider private constructor(config: Config) : AuthenticationProvider(config) {
    private val sessions = config.sessions

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.cookies[AdminRoutes.SESSION_COOKIE, CookieEncoding.RAW]?.takeIf { it.isNotBlank() }
        val principal = token?.let { sessions.resolve(it) }
        if (principal == null) {
            val cause = if (token == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            context.challenge(CHALLENGE_KEY, cause) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(AdminErrors.UNAUTHORIZED, "Not signed in"))
                challenge.complete()
            }
            return
        }
        context.principal(name, principal)
    }

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        lateinit var sessions: StaffSessionStore

        internal fun build() = StaffSessionAuthenticationProvider(this)
    }

    private companion object {
        const val CHALLENGE_KEY = "StaffSession"
    }
}

fun AuthenticationConfig.staffSession(
    name: String = STAFF_SESSION_AUTH,
    configure: StaffSessionAuthenticationProvider.Config.() -> Unit,
) {
    register(StaffSessionAuthenticationProvider.Config(name).apply(configure).build())
}
