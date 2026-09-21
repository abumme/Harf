package uz.abumme.harfgame.backend.admin.audit

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission

/** `GET /audit`: every entry for `AUDIT_READ_ALL`, the caller's own for `AUDIT_READ_OWN`. Read-only by design. */
fun Route.auditRoutes(admin: AdminBackend) {
    requirePermission(Permission.AUDIT_READ_ALL, Permission.AUDIT_READ_OWN) {
        get(adminPath(AdminRoutes.AUDIT)) {
            call.respond(HttpStatusCode.OK, admin.auditQueries.list(call.staffPrincipal(), AuditFilter.parse(call.request.queryParameters)))
        }
    }
}
