package uz.abumme.harfgame.backend.admin.access

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.api.ApiErrorResponse

/**
 * The role -> permission matrix, the only authority on what a role may do. Later changes add their permissions
 * to [Permission] and assign them here.
 */
val Role.permissions: Set<Permission>
    get() = when (this) {
        Role.ADMIN -> Permission.entries.toSet()
        Role.WORDER -> WORDER_PERMISSIONS
    }

private val WORDER_PERMISSIONS = setOf(
    Permission.WORDS_READ,
    Permission.WORDS_WRITE,
    Permission.SUGGESTIONS_REVIEW,
    Permission.AUDIT_READ_OWN,
    Permission.ACCOUNT_SELF,
)

class RequirePermissionConfig {
    /** The caller needs at least one of these. */
    var anyOf: Set<Permission> = emptySet()
}

/**
 * Route-scoped check that answers `403 forbidden` when the signed-in staff member has none of the configured
 * permissions. Installed under `authenticate("staff-session")`, so a request without a session was already
 * answered 401. Prefer [requirePermission] over installing it directly.
 */
val RequirePermission = createRouteScopedPlugin("RequirePermission", ::RequirePermissionConfig) {
    val anyOf = pluginConfig.anyOf
    on(AuthenticationChecked) { call ->
        if (call.isHandled) return@on
        val principal = call.principal<StaffPrincipal>() ?: return@on
        if (anyOf.none(principal::has)) {
            call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(AdminErrors.FORBIDDEN, "Not permitted"))
        }
    }
}

/** Routes in [build] require at least one of [anyOf]. */
fun Route.requirePermission(vararg anyOf: Permission, build: Route.() -> Unit): Route {
    require(anyOf.isNotEmpty()) { "requirePermission needs at least one permission" }
    val route = createChild(PermissionRouteSelector(anyOf.toSet()))
    route.install(RequirePermission) { this.anyOf = anyOf.toSet() }
    route.build()
    return route
}

/** Matches every request (it only anchors the plugin), like the selector `authenticate` uses. */
private class PermissionRouteSelector(private val permissions: Set<Permission>) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(requirePermission ${permissions.joinToString()})"
}
