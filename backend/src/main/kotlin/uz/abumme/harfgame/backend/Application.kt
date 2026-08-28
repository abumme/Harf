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
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.routes.authRoutes
import uz.abumme.harfgame.backend.routes.syncRoutes
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.service.SyncServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.auth.OAuthProvider

fun main() {
    DatabaseFactory.init()
    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt(), host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    jwtService: JwtService = JwtService(),
    verifiers: Map<OAuthProvider, OAuthVerifier> = mapOf(
        OAuthProvider.GOOGLE to GoogleOAuthVerifier(
            audiences = (System.getenv("GOOGLE_CLIENT_IDS") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        ),
        OAuthProvider.APPLE to AppleOAuthVerifier()
    ),
    authService: AuthServerService = AuthServerService(jwtService, verifiers),
    syncService: SyncServerService = SyncServerService(),
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
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse("internal_error", cause.message ?: "An unexpected error occurred")
            )
        }
    }

    routing {
        get("/") {
            call.respondText("Harf Backend is running")
        }
        authRoutes(authService)
        syncRoutes(syncService)
    }
}
