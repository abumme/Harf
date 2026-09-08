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
import uz.abumme.harfgame.backend.routes.authRoutes
import uz.abumme.harfgame.backend.routes.suggestionRoutes
import uz.abumme.harfgame.backend.routes.syncRoutes
import uz.abumme.harfgame.backend.routes.wordPackRoutes
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.SyncServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    // Long-poll for editor decisions in the background. Supervised: restart on unexpected failure so
    // a transient crash doesn't silently stop reviews (suggestions keep storing regardless).
    if (telegramBot.enabled) {
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                runCatching { telegramBot.runPolling() }
                    .onFailure { it.printStackTrace() }
                delay(5000)
            }
        }
    }

    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt(), host = "0.0.0.0") {
        module(
            wordPackService = wordPackService,
            suggestionService = suggestionService,
            telegramBot = telegramBot,
        )
    }.start(wait = true)
}

private fun telegramBotFromEnv(suggestionService: SuggestionServerService) = TelegramBot(
    botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: "",
    editorChatId = System.getenv("TELEGRAM_EDITOR_CHAT_ID") ?: "",
    editorIds = (System.getenv("TELEGRAM_EDITOR_IDS") ?: "")
        .split(",").mapNotNull { it.trim().toLongOrNull() }.toSet(),
    suggestions = suggestionService,
)

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
    telegramBot: TelegramBot = TelegramBot(
        botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: "",
        editorChatId = System.getenv("TELEGRAM_EDITOR_CHAT_ID") ?: "",
        editorIds = (System.getenv("TELEGRAM_EDITOR_IDS") ?: "")
            .split(",").mapNotNull { it.trim().toLongOrNull() }.toSet(),
        suggestions = suggestionService,
    ),
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
        suggestionRoutes(suggestionService, telegramBot)
    }
}
