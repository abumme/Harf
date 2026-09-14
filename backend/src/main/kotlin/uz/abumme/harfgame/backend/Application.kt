package uz.abumme.harfgame.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.backend.auth.oauth.AppleOAuthVerifier
import uz.abumme.harfgame.backend.auth.oauth.GoogleOAuthVerifier
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.backend.config.ServerConfig
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.dictionary.WiktionaryLookup
import uz.abumme.harfgame.backend.review.DailyReportScheduler
import uz.abumme.harfgame.backend.review.SuggestionReviewWorker
import java.time.Instant
import uz.abumme.harfgame.backend.routes.authRoutes
import uz.abumme.harfgame.backend.routes.suggestionRoutes
import uz.abumme.harfgame.backend.routes.syncRoutes
import uz.abumme.harfgame.backend.routes.wordPackRoutes
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.SyncServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.HttpTelegramApi
import uz.abumme.harfgame.backend.telegram.TelegramBot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.auth.OAuthProvider

fun main() {
    // Fail fast rather than silently running production on the dev JWT secret.
    ServerConfig.requireSecureProductionConfig()
    DatabaseFactory.init()
    val wordPackService = WordPackServerService()
    runBlocking { wordPackService.seed() } // idempotent: seeds version 1 if absent
    val suggestionService = SuggestionServerService(wordPackService)
    val telegramBot = telegramBotFromEnv(suggestionService)
    val reviewWorker = SuggestionReviewWorker(
        suggestions = suggestionService,
        lookup = WiktionaryLookup(enabled = wordLookupEnabled(System.getenv("WORD_LOOKUP_ENABLED"))),
        telegram = telegramBot,
    )
    val dailyReports = DailyReportScheduler(suggestionService, wordPackService, telegramBot)

    // Background work, each loop supervised so a transient failure delays it instead of stopping it.
    // The review worker runs even without a bot: verified words still reach the pack.
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    background.superviseForever(REVIEW_INTERVAL_MILLIS) { reviewWorker.runOnce() }
    if (telegramBot.enabled) {
        background.superviseForever(POLL_RESTART_MILLIS) { telegramBot.runPolling() } // long-polls until it fails
        background.superviseForever(REPORT_INTERVAL_MILLIS) { dailyReports.sendDue(Instant.now()) }
    }

    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt(), host = "0.0.0.0") {
        module(
            wordPackService = wordPackService,
            suggestionService = suggestionService,
        )
    }.start(wait = true)
}

/** The editor bot from `TELEGRAM_*` env; a blank or missing token leaves it disabled. */
private fun telegramBotFromEnv(suggestionService: SuggestionServerService) = TelegramBot(
    editorChatId = System.getenv("TELEGRAM_EDITOR_CHAT_ID") ?: "",
    editorIds = (System.getenv("TELEGRAM_EDITOR_IDS") ?: "")
        .split(",").mapNotNull { it.trim().toLongOrNull() }.toSet(),
    topics = parseTelegramTopics(System.getenv("TELEGRAM_TOPICS")),
    suggestions = suggestionService,
    api = System.getenv("TELEGRAM_BOT_TOKEN")?.takeIf { it.isNotBlank() }?.let { HttpTelegramApi(it) },
)

/** Parse "en=123,ru=456,uz-latn=789" into a lang -> topic (message_thread_id) map. */
internal fun parseTelegramTopics(raw: String?): Map<String, Int> =
    (raw ?: "").split(",").mapNotNull { entry ->
        val (lang, id) = entry.split("=").map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        val threadId = id.toIntOrNull() ?: return@mapNotNull null
        if (lang.isEmpty()) null else lang to threadId
    }.toMap()

private const val REVIEW_INTERVAL_MILLIS = 5_000L
private const val POLL_RESTART_MILLIS = 5_000L
private const val REPORT_INTERVAL_MILLIS = 60_000L

/** `WORD_LOOKUP_ENABLED`: automatic dictionary verification is on unless the value is "false". */
internal fun wordLookupEnabled(raw: String?): Boolean = raw?.trim()?.equals("false", ignoreCase = true) != true

/**
 * Runs [block] now and again every [intervalMillis] until the scope is cancelled. A failure is logged and the
 * loop carries on after the interval, so one bad run never stops background work.
 */
internal fun CoroutineScope.superviseForever(intervalMillis: Long, block: suspend () -> Unit): Job = launch {
    while (isActive) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
        delay(intervalMillis)
    }
}

fun Application.module(
    jwtService: JwtService = JwtService(),
    verifiers: Map<OAuthProvider, OAuthVerifier> = mapOf(
        OAuthProvider.GOOGLE to GoogleOAuthVerifier(
            audiences = (System.getenv("GOOGLE_CLIENT_IDS") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        ),
        OAuthProvider.APPLE to AppleOAuthVerifier(
            audiences = (System.getenv("APPLE_AUDIENCES") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
    ),
    authService: AuthServerService = AuthServerService(jwtService, verifiers),
    syncService: SyncServerService = SyncServerService(),
    wordPackService: WordPackServerService = WordPackServerService(),
    suggestionService: SuggestionServerService = SuggestionServerService(wordPackService),
) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
        })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwtService.verifier)
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            // Always answer an unauthorized request with a decodable JSON body so the client can
            // detect the 401 before attempting to parse it, and trigger a token refresh.
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse("unauthorized", "Missing or invalid access token")
                )
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Keep internal detail in server logs only; never return it to the client.
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse("internal_error", "An unexpected error occurred")
            )
        }
    }

    routing {
        get("/") {
            call.respondText("Harf Backend is running")
        }
        authRoutes(authService)
        syncRoutes(syncService)
        wordPackRoutes(wordPackService)
        suggestionRoutes(suggestionService)
    }
}
