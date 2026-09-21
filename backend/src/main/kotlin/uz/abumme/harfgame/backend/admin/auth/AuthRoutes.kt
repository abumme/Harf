package uz.abumme.harfgame.backend.admin.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.api.ApiErrorResponse
import io.ktor.http.CookieEncoding

/** Per-address limiter for sign-in attempts. */
val LOGIN_RATE_LIMIT = RateLimitName("admin-login")

/** `POST /auth/login`: public, rate limited. Mounted inside the admin prefix route. */
fun Route.loginRoute(admin: AdminBackend) {
    rateLimit(LOGIN_RATE_LIMIT) {
        post(adminPath(AdminRoutes.AUTH_LOGIN)) {
            val request = call.receiveAdmin<LoginRequest>()
            val result = admin.auth.login(
                rawUsername = request.username,
                password = request.password,
                ip = call.request.origin.remoteAddress,
                userAgent = call.request.header(HttpHeaders.UserAgent),
            )
            when (result) {
                is LoginResult.Success -> {
                    admin.cookies.setSession(call, result.sessionToken)
                    call.respond(HttpStatusCode.OK, result.me)
                }
                LoginResult.InvalidCredentials -> call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(AdminErrors.UNAUTHORIZED, "Invalid username or password"),
                )
                LoginResult.Locked -> call.respond(
                    HttpStatusCode.Locked,
                    ApiErrorResponse(AdminErrors.LOCKED, "Too many failed sign-in attempts; try again later"),
                )
            }
        }
    }
}

/** Session routes: logout, me and own password change. Mounted under `authenticate(STAFF_SESSION_AUTH)`. */
fun Route.sessionRoutes(admin: AdminBackend) {
    post(adminPath(AdminRoutes.AUTH_LOGOUT)) {
        admin.auth.logout(call.staffPrincipal())
        admin.cookies.clear(call)
        call.respond(HttpStatusCode.NoContent)
    }

    get(adminPath(AdminRoutes.AUTH_ME)) {
        val me = admin.auth.me(call.staffPrincipal())
        // Re-issue the readable anti-forgery cookie if the browser lost it.
        if (call.request.cookies[AdminRoutes.XSRF_COOKIE, CookieEncoding.RAW].isNullOrEmpty()) {
            call.request.cookies[AdminRoutes.SESSION_COOKIE, CookieEncoding.RAW]?.let { admin.cookies.setXsrf(call, it) }
        }
        call.respond(HttpStatusCode.OK, me)
    }

    requirePermission(Permission.ACCOUNT_SELF) {
        post(adminPath(AdminRoutes.AUTH_PASSWORD)) {
            admin.auth.changePassword(call.staffPrincipal(), call.receiveAdmin<ChangePasswordRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
