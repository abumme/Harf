package uz.abumme.harfgame.backend.admin.analytics

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable

/**
 * Aggregated analytics (`ANALYTICS_READ`, ADMIN only): the overview tiles, and for every table a JSON endpoint and a
 * CSV export with the same parameters (`from`, `to`, `lang`, and `calendar`/`sort`/`dir` for words). A range longer
 * than 366 days or ending before it starts is refused with `422` before any query runs.
 */
fun Route.analyticsRoutes(admin: AdminBackend) {
    val analytics = admin.analytics
    requirePermission(Permission.ANALYTICS_READ) {
        get(adminPath(AdminRoutes.ANALYTICS_OVERVIEW)) {
            call.respond(HttpStatusCode.OK, analytics.overview())
        }
        for (table in AnalyticsTable.entries) {
            get(adminPath(AdminRoutes.analytics(table))) {
                val query = analytics.parse(call.queryParameters())
                when (table) {
                    AnalyticsTable.ACCOUNTS -> call.respond(HttpStatusCode.OK, analytics.accounts(query))
                    AnalyticsTable.ACTIVITY -> call.respond(HttpStatusCode.OK, analytics.activity(query))
                    AnalyticsTable.RETENTION -> call.respond(HttpStatusCode.OK, analytics.retention(query))
                    AnalyticsTable.STREAKS -> call.respond(HttpStatusCode.OK, analytics.streaks(query))
                    AnalyticsTable.OUTCOMES -> call.respond(HttpStatusCode.OK, analytics.outcomes(query))
                    AnalyticsTable.WORDS -> call.respond(HttpStatusCode.OK, analytics.words(query))
                    AnalyticsTable.SUGGESTIONS -> call.respond(HttpStatusCode.OK, analytics.suggestions(query))
                    AnalyticsTable.CONTENT -> call.respond(HttpStatusCode.OK, analytics.content(query))
                    AnalyticsTable.STAFF -> call.respond(HttpStatusCode.OK, analytics.staff(query))
                }
            }
            get(adminPath(AdminRoutes.analyticsCsv(table))) {
                val query = analytics.parse(call.queryParameters())
                val sheet = when (table) {
                    AnalyticsTable.ACCOUNTS -> AnalyticsCsv.accounts(analytics.accounts(query))
                    AnalyticsTable.ACTIVITY -> AnalyticsCsv.activity(analytics.activity(query))
                    AnalyticsTable.RETENTION -> AnalyticsCsv.retention(analytics.retention(query))
                    AnalyticsTable.STREAKS -> AnalyticsCsv.streaks(analytics.streaks(query))
                    AnalyticsTable.OUTCOMES -> AnalyticsCsv.outcomes(analytics.outcomes(query))
                    AnalyticsTable.WORDS -> AnalyticsCsv.words(analytics.words(query))
                    AnalyticsTable.SUGGESTIONS -> AnalyticsCsv.suggestions(analytics.suggestions(query))
                    AnalyticsTable.CONTENT -> AnalyticsCsv.content(analytics.content(query))
                    AnalyticsTable.STAFF -> AnalyticsCsv.staff(analytics.staff(query))
                }
                // The name holds only letters, digits, dashes and a dot; quoted as the browser download expects.
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${AnalyticsCsv.fileName(table, query)}\"")
                call.respondBytes(AnalyticsCsv.encode(sheet), ContentType.Text.CSV.withCharset(Charsets.UTF_8), HttpStatusCode.OK)
            }
        }
    }
}

private fun RoutingCall.queryParameters(): Map<String, String> {
    val parameters = request.queryParameters
    return parameters.names().associateWith { parameters[it].orEmpty() }
}
