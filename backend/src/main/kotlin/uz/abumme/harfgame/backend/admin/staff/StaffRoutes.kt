package uz.abumme.harfgame.backend.admin.staff

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.ResetPasswordRequest
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest

/** Staff management (ADMIN). There is deliberately no delete route: staff are disabled, never deleted. */
fun Route.staffRoutes(admin: AdminBackend) {
    val staff = adminPath(AdminRoutes.STAFF)
    requirePermission(Permission.STAFF_MANAGE) {
        get(staff) {
            call.respond(HttpStatusCode.OK, admin.staff.list())
        }
        post(staff) {
            call.respond(HttpStatusCode.Created, admin.staff.create(call.staffPrincipal(), call.receiveAdmin<CreateStaffRequest>()))
        }
        get("$staff/{id}") {
            call.respond(HttpStatusCode.OK, admin.staff.get(call.staffId()))
        }
        patch("$staff/{id}") {
            call.respond(HttpStatusCode.OK, admin.staff.update(call.staffPrincipal(), call.staffId(), call.receiveAdmin<UpdateStaffRequest>()))
        }
        post("$staff/{id}/password") {
            admin.staff.resetPassword(call.staffPrincipal(), call.staffId(), call.receiveAdmin<ResetPasswordRequest>().newPassword)
            call.respond(HttpStatusCode.NoContent)
        }
        post("$staff/{id}/disable") {
            call.respond(HttpStatusCode.OK, admin.staff.disable(call.staffPrincipal(), call.staffId()))
        }
        post("$staff/{id}/enable") {
            call.respond(HttpStatusCode.OK, admin.staff.enable(call.staffPrincipal(), call.staffId()))
        }
    }
}

private fun RoutingCall.staffId(): String =
    parameters["id"]?.takeIf { it.isNotBlank() } ?: throw AdminApiException.notFound("Missing staff id")
