package uz.abumme.harfgame.backend.admin.auth

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import uz.abumme.harfgame.backend.admin.AdminConfig
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.api.ApiErrorResponse
import java.security.MessageDigest
import java.util.Base64

private val MUTATING_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

/**
 * Session-bound anti-forgery token: `base64url(SHA-256("xsrf:" + sessionToken))`. Derived, so it needs no storage,
 * and a token taken from another session never matches.
 */
object Xsrf {
    fun tokenFor(sessionToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("xsrf:$sessionToken".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun matches(sessionToken: String?, headerValue: String?): Boolean {
        if (sessionToken.isNullOrEmpty() || headerValue.isNullOrEmpty()) return false
        return MessageDigest.isEqual(tokenFor(sessionToken).toByteArray(), headerValue.toByteArray())
    }
}

/**
 * Requires `X-XSRF-TOKEN` to match the session's token on every POST/PUT/PATCH/DELETE, else `403 forbidden` before
 * the handler runs. Installed on the authenticated admin routes (login has no session yet and is exempt).
 */
val XsrfProtection = createRouteScopedPlugin("XsrfProtection") {
    on(AuthenticationChecked) { call ->
        if (call.isHandled || call.request.httpMethod !in MUTATING_METHODS) return@on
        val sessionToken = call.request.cookies[AdminRoutes.SESSION_COOKIE, CookieEncoding.RAW]
        if (!Xsrf.matches(sessionToken, call.request.headers[AdminRoutes.XSRF_HEADER])) {
            call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(AdminErrors.FORBIDDEN, "Missing or invalid anti-forgery token"))
        }
    }
}

/**
 * Admin request bodies are JSON only: a state-changing request carrying a body of any other type gets
 * `415 unsupported_media_type`. A cross-site HTML form cannot send `application/json`, which, with `SameSite=Strict`,
 * is what protects the login request.
 */
val RequireJsonBody = createRouteScopedPlugin("RequireJsonBody") {
    onCall { call ->
        if (call.request.httpMethod !in MUTATING_METHODS) return@onCall
        val hasBody = (call.request.contentLength() ?: 0L) > 0L || call.request.headers[HttpHeaders.TransferEncoding] != null
        if (hasBody && !call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ApiErrorResponse("unsupported_media_type", "Admin requests must send application/json"),
            )
        }
    }
}

/** Sets and clears the session and anti-forgery cookies with the deployment's path and `Secure` flag. */
class AdminCookies(private val config: AdminConfig) {

    /** Session cookie (HttpOnly) plus its readable anti-forgery cookie. */
    fun setSession(call: ApplicationCall, sessionToken: String) {
        call.response.cookies.append(cookie(AdminRoutes.SESSION_COOKIE, sessionToken, httpOnly = true))
        setXsrf(call, sessionToken)
    }

    fun setXsrf(call: ApplicationCall, sessionToken: String) {
        call.response.cookies.append(cookie(AdminRoutes.XSRF_COOKIE, Xsrf.tokenFor(sessionToken), httpOnly = false))
    }

    fun clear(call: ApplicationCall) {
        call.response.cookies.append(cookie(AdminRoutes.SESSION_COOKIE, "", httpOnly = true, maxAgeSeconds = 0))
        call.response.cookies.append(cookie(AdminRoutes.XSRF_COOKIE, "", httpOnly = false, maxAgeSeconds = 0))
    }

    private fun cookie(name: String, value: String, httpOnly: Boolean, maxAgeSeconds: Int = SESSION_MAX_AGE_SECONDS) = Cookie(
        name = name,
        value = value,
        encoding = CookieEncoding.RAW,
        // Never outlives the session's absolute lifetime.
        maxAge = maxAgeSeconds,
        path = config.cookiePath,
        secure = config.secureCookies,
        httpOnly = httpOnly,
        extensions = mapOf("SameSite" to "Strict"),
    )

    private companion object {
        val SESSION_MAX_AGE_SECONDS = StaffSessionStore.ABSOLUTE_LIFETIME.seconds.toInt()
    }
}
