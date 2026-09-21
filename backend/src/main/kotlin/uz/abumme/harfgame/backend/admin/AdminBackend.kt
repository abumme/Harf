package uz.abumme.harfgame.backend.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.util.reflect.typeInfo
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsRollupJob
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsService
import uz.abumme.harfgame.backend.admin.analytics.GameResultRecorder
import uz.abumme.harfgame.backend.admin.analytics.ResultsSweep
import uz.abumme.harfgame.backend.admin.analytics.analyticsRoutes
import uz.abumme.harfgame.backend.admin.answerpool.AnswerPoolService
import uz.abumme.harfgame.backend.admin.answerpool.answerPoolRoutes
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.AuditService
import uz.abumme.harfgame.backend.admin.audit.auditRoutes
import uz.abumme.harfgame.backend.admin.auth.AdminCookies
import uz.abumme.harfgame.backend.admin.auth.LOGIN_RATE_LIMIT
import uz.abumme.harfgame.backend.admin.auth.PasswordHasher
import uz.abumme.harfgame.backend.admin.auth.RequireJsonBody
import uz.abumme.harfgame.backend.admin.auth.STAFF_SESSION_AUTH
import uz.abumme.harfgame.backend.admin.auth.StaffAuthService
import uz.abumme.harfgame.backend.admin.auth.StaffBootstrap
import uz.abumme.harfgame.backend.admin.auth.StaffSessionStore
import uz.abumme.harfgame.backend.admin.auth.XsrfProtection
import uz.abumme.harfgame.backend.admin.auth.loginRoute
import uz.abumme.harfgame.backend.admin.auth.sessionRoutes
import uz.abumme.harfgame.backend.admin.auth.staffSession
import uz.abumme.harfgame.backend.admin.calendar.CalendarService
import uz.abumme.harfgame.backend.admin.calendar.DailyCalendarScheduler
import uz.abumme.harfgame.backend.admin.calendar.calendarRoutes
import uz.abumme.harfgame.backend.admin.languages.languagesRoutes
import uz.abumme.harfgame.backend.admin.players.PlayersService
import uz.abumme.harfgame.backend.admin.players.playersRoutes
import uz.abumme.harfgame.backend.admin.staff.StaffService
import uz.abumme.harfgame.backend.admin.suggestions.SuggestionsService
import uz.abumme.harfgame.backend.admin.suggestions.suggestionsRoutes
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.admin.words.wordsRoutes
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.backend.admin.staff.staffRoutes
import uz.abumme.harfgame.backend.admin.web.adminWebRoutes
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.api.ApiErrorResponse
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The staff admin backend: its services and configuration, wired once per application. Later changes add their
 * services here and their routes in [adminRoutes].
 *
 * The word catalog and suggestion services are shared with the player API and the background work (`main` passes
 * the same instances); tests get fresh ones on the injected [clock].
 */
class AdminBackend(
    val config: AdminConfig = AdminConfig.fromEnv(),
    /** Languages that have a word pack: every language an ADMIN has, and the only ones staff can be assigned. */
    val packLanguages: suspend () -> List<String>,
    val clock: Clock = Clock.systemUTC(),
    val passwordHasher: PasswordHasher = PasswordHasher(),
    val wordPacks: WordPackServerService = WordPackServerService(),
    val catalog: WordCatalogService = WordCatalogService(wordPacks, clock),
    val suggestionService: SuggestionServerService = SuggestionServerService(wordPacks, catalog = catalog),
    /** The editors' bot, whose decision messages a panel decision updates; null or disabled leaves Telegram alone. */
    val telegram: TelegramBot? = null,
    /** The players' auth service: an ADMIN's deletion and end-sessions reuse its paths (Apple revocation included). */
    val playerAuth: AuthServerService = AuthServerService(JwtService()),
) {
    val sessions = StaffSessionStore(clock)
    val audit = AuditLog(clock)
    val cookies = AdminCookies(config)
    val auth = StaffAuthService(clock, passwordHasher, sessions, audit, packLanguages)
    val staff = StaffService(clock, passwordHasher, sessions, audit, packLanguages)
    val auditQueries = AuditService()
    val bootstrap = StaffBootstrap(clock, passwordHasher, sessions, audit)
    val suggestions = SuggestionsService(suggestionService, telegram, packLanguages)

    /** The daily-word calendars and answer pools; they share the catalog's clock, locks, publisher and audit writer. */
    val calendar = CalendarService(catalog)
    val answerPool = AnswerPoolService(catalog)
    val calendarScheduler = DailyCalendarScheduler(calendar)

    /** Player accounts: search, detail and the support actions. */
    val players = PlayersService(clock, audit, playerAuth)

    /** Aggregated analytics: the dashboard's reads, and the rollup job `main` runs every 15 minutes. */
    val analytics = AnalyticsService(catalog, clock)
    val analyticsRollup = AnalyticsRollupJob(catalog, ResultsSweep(GameResultRecorder(clock)))
}

/** Application-level plugins the admin API needs: trusted forwarded headers and the login rate limiter. */
fun Application.installAdminPlugins(admin: AdminBackend) {
    if (admin.config.trustProxyHeaders) {
        // Behind Caddy the peer is the proxy (the Docker bridge gateway); the client is the last X-Forwarded-For entry.
        install(XForwardedHeaders) { useLastProxy() }
    }
    install(RateLimit) {
        register(LOGIN_RATE_LIMIT) {
            rateLimiter(limit = admin.config.loginAttemptsPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
}

/** The staff session provider, registered next to the players' `auth-jwt`. */
fun AuthenticationConfig.adminAuthentication(admin: AdminBackend) {
    staffSession { sessions = admin.sessions }
}

/** Error answers of the admin API; the player API keeps its own behaviour. */
fun StatusPagesConfig.adminErrorHandlers() {
    exception<AdminApiException> { call, cause ->
        call.respond(cause.status, ApiErrorResponse(cause.code, cause.message))
    }
    // Only the admin login route is rate limited; RateLimit itself answers without a body.
    status(HttpStatusCode.TooManyRequests) { call, status ->
        call.respond(status, ApiErrorResponse(AdminErrors.RATE_LIMITED, "Too many sign-in attempts; try again in a minute"))
    }
}

/** The admin API under [AdminRoutes.PREFIX], plus the exported panel at `/admin` when configured. */
fun Route.adminRoutes(admin: AdminBackend) {
    route(AdminRoutes.PREFIX) {
        admin.config.devCorsOrigin?.let { origin -> installDevCors(origin) }
        install(RequireJsonBody)

        loginRoute(admin)
        authenticate(STAFF_SESSION_AUTH) {
            install(XsrfProtection)
            sessionRoutes(admin)
            languagesRoutes(admin)
            staffRoutes(admin)
            auditRoutes(admin)
            wordsRoutes(admin)
            suggestionsRoutes(admin)
            answerPoolRoutes(admin)
            calendarRoutes(admin)
            playersRoutes(admin)
            analyticsRoutes(admin)
        }
    }
    adminWebRoutes(admin.config.webDir)
}

/**
 * Development only: lets the Kobweb dev server (another port, same site) call the API with credentials.
 * [AdminConfig.devCorsOrigin] is always null in production, so this is never installed there.
 */
private fun Route.installDevCors(origin: String) {
    val scheme = origin.substringBefore("://", missingDelimiterValue = "http")
    val host = origin.substringAfter("://")
    install(CORS) {
        allowHost(host, schemes = listOf(scheme))
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(AdminRoutes.XSRF_HEADER)
    }
}

/** A route path relative to the admin prefix, from its full [AdminRoutes] constant. */
internal fun adminPath(fullPath: String): String = fullPath.removePrefix(AdminRoutes.PREFIX)

/** The authenticated staff member; admin routes only run under `authenticate(STAFF_SESSION_AUTH)`. */
internal fun ApplicationCall.staffPrincipal(): StaffPrincipal =
    principal<StaffPrincipal>() ?: throw IllegalStateException("Admin route reached without a staff session")

/** Receives a JSON body; a malformed or incomplete one answers `400 bad_request` instead of a server error. */
internal suspend inline fun <reified T : Any> ApplicationCall.receiveAdmin(): T =
    try {
        receive(typeInfo<T>())
    } catch (e: BadRequestException) {
        throw AdminApiException.badRequest("Malformed request body")
    } catch (e: ContentTransformationException) {
        throw AdminApiException.badRequest("Malformed request body")
    }
