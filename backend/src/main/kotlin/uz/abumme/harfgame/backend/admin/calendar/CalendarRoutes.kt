package uz.abumme.harfgame.backend.admin.calendar

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.calendar.CalendarParams
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The daily-word calendars (ADMIN only: `CALENDAR_MANAGE`; a WORDER is refused before any handler runs). */
fun Route.calendarRoutes(admin: AdminBackend) {
    val base = adminPath(AdminRoutes.CALENDAR)
    requirePermission(Permission.CALENDAR_MANAGE) {
        get(adminPath(AdminRoutes.CALENDAR_NOTICES)) {
            val includeDismissed = call.request.queryParameters[CalendarParams.INCLUDE_DISMISSED] == "true"
            call.respond(HttpStatusCode.OK, admin.calendar.notices(includeDismissed))
        }
        post("${adminPath(AdminRoutes.CALENDAR_NOTICES)}/{id}/dismiss") {
            admin.calendar.dismiss(call.staffPrincipal(), call.pathParameter("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        get("$base/{calendar}") {
            val parameters = call.request.queryParameters
            val query = DayQuery(
                from = parameters.date(CalendarParams.FROM),
                to = parameters.date(CalendarParams.TO),
                source = parameters[CalendarParams.SOURCE]?.takeIf { it.isNotBlank() }?.let { raw ->
                    DaySource.entries.firstOrNull { it.name == raw }
                        ?: throw AdminApiException.validation(CalendarParams.SOURCE, FieldReasons.INVALID)
                },
                repeatsOnly = parameters[CalendarParams.REPEATS_ONLY] == "true",
                page = parameters.page(),
                size = parameters.size(),
            )
            call.respond(HttpStatusCode.OK, admin.calendar.days(call.pathParameter("calendar"), query))
        }
        get("$base/{calendar}/candidates") {
            val parameters = call.request.queryParameters
            val page = admin.calendar.candidates(
                call.pathParameter("calendar"),
                parameters.date(CalendarParams.DAY),
                parameters[CalendarParams.Q],
                parameters.page(),
                parameters.size(),
            )
            call.respond(HttpStatusCode.OK, page)
        }
        put("$base/{calendar}/days/{day}") {
            val request = call.receiveAdmin<PickDayRequest>()
            call.respond(HttpStatusCode.OK, admin.calendar.pick(call.staffPrincipal(), call.pathParameter("calendar"), call.day(), request))
        }
        delete("$base/{calendar}/days/{day}") {
            call.respond(HttpStatusCode.OK, admin.calendar.unpick(call.staffPrincipal(), call.pathParameter("calendar"), call.day()))
        }
    }
}

internal fun RoutingCall.pathParameter(name: String): String =
    parameters[name]?.takeIf { it.isNotBlank() } ?: throw AdminApiException.notFound("Missing $name")

private fun RoutingCall.day(): LocalDate =
    parseDay(pathParameter("day")) ?: throw AdminApiException.validation("day", FieldReasons.INVALID)

private fun parseDay(raw: String): LocalDate? = try {
    LocalDate.parse(raw)
} catch (e: DateTimeParseException) {
    null
}

private fun Parameters.date(name: String): LocalDate? =
    this[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { parseDay(it) ?: throw AdminApiException.validation(name, FieldReasons.INVALID) }

/** The zero-based `page` parameter (calendar and answer pool); a malformed one answers 422. */
internal fun Parameters.page(): Int {
    val raw = this[CalendarParams.PAGE]?.trim()?.takeIf { it.isNotEmpty() } ?: return 0
    return raw.toIntOrNull()?.takeIf { it >= 0 } ?: throw AdminApiException.validation(CalendarParams.PAGE, FieldReasons.INVALID)
}

/** The `size` parameter, 1 to [CalendarParams.MAX_SIZE]; a malformed one answers 422. */
internal fun Parameters.size(): Int {
    val raw = this[CalendarParams.SIZE]?.trim()?.takeIf { it.isNotEmpty() } ?: return CalendarParams.DEFAULT_SIZE
    return raw.toIntOrNull()?.takeIf { it in 1..CalendarParams.MAX_SIZE }
        ?: throw AdminApiException.validation(CalendarParams.SIZE, FieldReasons.INVALID)
}
