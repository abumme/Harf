package uz.abumme.harfgame.backend.admin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.auth.PasswordHasher
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.clearDailyCalendars
import uz.abumme.harfgame.backend.defaultAnswers
import uz.abumme.harfgame.backend.module
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiErrorResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

// Shared fixtures for the admin API tests.

/** A clock tests move by hand. */
class MutableClock(var now: Instant = Instant.parse("2026-09-16T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(duration: java.time.Duration) {
        now = now.plus(duration)
    }
}

/** Cheap Argon2id parameters: the integration tests hash and verify many times. */
val testHasher = PasswordHasher(memoryKib = 64, iterations = 1, parallelism = 1)

const val PASSWORD = "correct horse battery"
val PACK_LANGUAGES = listOf("en", "kk", "ru", "uz-cyrl", "uz-latn")

val adminJson = Json { ignoreUnknownKeys = true }

/**
 * Empties the staff tables, suggestions and the word catalog, and leaves one tiny valid word pack per launch language
 * (one daily answer, carried into the catalog as it would be at startup).
 */
fun resetAdminData() {
    transaction(DatabaseFactory.init()) {
        StaffAuditLogTable.deleteAll()
        clearDailyCalendars()
        WordsTable.deleteAll()
        WordSuggestionsTable.deleteAll()
        StaffSessionsTable.deleteAll()
        StaffLanguagesTable.deleteAll()
        StaffTable.deleteAll()
        WordPacksTable.deleteAll()
        for (lang in PACK_LANGUAGES) {
            val words = Json.encodeToString(defaultAnswers(lang))
            WordPacksTable.insert {
                it[WordPacksTable.lang] = lang
                it[version] = "1"
                it[effectiveFrom] = 0L
                it[anchorEpochDay] = 0L
                it[answers] = words
                it[guesses] = words
                it[schedule] = words
                it[updatedAt] = Instant.now()
            }
        }
    }
    runBlocking { WordCatalogService(WordPackServerService()).carryOver() }
    // The carry-over's own audit entries are not what admin tests look at.
    transaction(DatabaseFactory.init()) { StaffAuditLogTable.deleteAll() }
}

/** Inserts a staff account directly and returns its id. */
fun insertStaff(
    username: String,
    role: Role = Role.WORDER,
    languages: List<String> = if (role == Role.WORDER) listOf("ru") else emptyList(),
    status: StaffStatus = StaffStatus.ACTIVE,
    password: String = PASSWORD,
    telegramUserId: Long? = null,
    now: Instant = Instant.now(),
): String {
    val id = UUID.randomUUID().toString()
    val hash = testHasher.hashBlocking(password)
    transaction(DatabaseFactory.init()) {
        StaffTable.insert {
            it[StaffTable.id] = id
            it[StaffTable.username] = username
            it[displayName] = null
            it[StaffTable.role] = role.name
            it[StaffTable.status] = status.name
            it[passwordHash] = hash
            it[passwordChangedAt] = now
            it[failedLoginCount] = 0
            it[lockedUntil] = null
            it[StaffTable.telegramUserId] = telegramUserId
            it[createdAt] = now
            it[createdBy] = null
            it[updatedAt] = now
            it[lastLoginAt] = null
        }
        StaffLanguagesTable.batchInsert(languages) { lang ->
            this[StaffLanguagesTable.staffId] = id
            this[StaffLanguagesTable.lang] = lang
        }
    }
    return id
}

fun staffRow(id: String) = transaction(DatabaseFactory.init()) {
    StaffTable.selectAll().where { StaffTable.id eq id }.single()
}

/** Audit rows, oldest first. */
fun auditRows() = transaction(DatabaseFactory.init()) {
    StaffAuditLogTable.selectAll().orderBy(StaffAuditLogTable.at).toList()
}

fun adminBackend(
    clock: Clock = Clock.systemUTC(),
    config: AdminConfig = AdminConfig(loginAttemptsPerMinute = 1_000),
) = AdminBackend(config = config, packLanguages = { WordPackServerService().languages() }, clock = clock, passwordHasher = testHasher)

/** Starts the real application module with [admin] and returns a JSON client. */
fun ApplicationTestBuilder.adminClient(admin: AdminBackend = adminBackend()): HttpClient {
    application { module(admin = admin) }
    return createClient { install(ContentNegotiation) { json(adminJson) } }
}

/** The cookies a successful sign-in sets. */
data class StaffSession(val token: String, val xsrf: String)

suspend fun HttpClient.login(username: String, password: String = PASSWORD, forwardedFor: String? = null): HttpResponse =
    post(AdminRoutes.AUTH_LOGIN) {
        contentType(ContentType.Application.Json)
        forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) }
        setBody(LoginRequest(username, password))
    }

fun HttpResponse.session(): StaffSession {
    val cookies = headers.getAll(HttpHeaders.SetCookie).orEmpty().map(::parseServerSetCookieHeader).associateBy { it.name }
    return StaffSession(
        token = cookies.getValue(AdminRoutes.SESSION_COOKIE).value,
        xsrf = cookies.getValue(AdminRoutes.XSRF_COOKIE).value,
    )
}

suspend fun HttpClient.signIn(username: String, password: String = PASSWORD): StaffSession {
    val response = login(username, password)
    check(response.status.value == 200) { "sign-in of $username failed: ${response.status} ${response.bodyAsText()}" }
    return response.session()
}

/** Sends the session cookie and, unless [xsrf] is null, the anti-forgery header. */
fun HttpRequestBuilder.withSession(session: StaffSession, xsrf: String? = session.xsrf) {
    header(HttpHeaders.Cookie, "${AdminRoutes.SESSION_COOKIE}=${session.token}; ${AdminRoutes.XSRF_COOKIE}=${session.xsrf}")
    xsrf?.let { header(AdminRoutes.XSRF_HEADER, it) }
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpResponse.error(): ApiErrorResponse = adminJson.decodeFromString(bodyAsText())
