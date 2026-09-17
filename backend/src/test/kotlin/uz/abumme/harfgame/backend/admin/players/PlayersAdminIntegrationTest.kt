package uz.abumme.harfgame.backend.admin.players

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.adminJson
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.error
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.jsonBody
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.EndSessionsResultDto
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerParams
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerType
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayersAdminIntegrationTest {

    private val unknown = "3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"

    @BeforeTest
    fun setup() = resetPlayerData()

    /** Every player-account route for [id], as a call that applies the given headers (session, token or none). */
    private fun routes(id: String): List<Pair<String, suspend HttpClient.(HttpRequestBuilder.() -> Unit) -> HttpResponse>> = listOf(
        "search" to { block -> get(AdminRoutes.PLAYERS + "?q=Grace") { block() } },
        "detail" to { block -> get(AdminRoutes.player(id)) { block() } },
        "delete" to { block -> post(AdminRoutes.playerDelete(id)) { block(); jsonBody(DeletePlayerRequest(id)) } },
        "end sessions" to { block -> post(AdminRoutes.playerEndSessions(id)) { block() } },
        "block" to { block -> put(AdminRoutes.playerSuggestionBlock(id)) { block() } },
        "unblock" to { block -> delete(AdminRoutes.playerSuggestionBlock(id)) { block() } },
        "clear name" to { block -> delete(AdminRoutes.playerDisplayName(id)) { block() } },
    )

    /** Decodes a response body strictly into a shared type: an unknown or missing field fails the test. */
    private suspend inline fun <reified T> HttpResponse.shared(): T = kotlinx.serialization.json.Json.decodeFromString(bodyAsText())

    private fun searchUrl(query: PlayerSearchQuery): String =
        AdminRoutes.PLAYERS + query.toQueryParameters().joinToString("&", prefix = "?") { (name, value) ->
            "$name=${java.net.URLEncoder.encode(value, Charsets.UTF_8)}"
        }

    @Test
    fun anAdminSearchesViewsAndActsOnAPlayerThroughTheSharedTypes() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val grace = insertPlayer("Grace", Instant.parse("2026-09-10T08:00:00Z"), identities = listOf(OAuthProvider.GOOGLE to "google-subject-1"))
        insertPlayer("Vali", Instant.parse("2026-09-11T08:00:00Z"))
        insertStats(grace, listOf(ResultRecordDto("ru", 20_000, true, 4)))
        insertSuggestion("ru", "берег", author = grace)
        val client = adminClient()
        val boss = client.signIn("boss")

        val search = client.get(searchUrl(PlayerSearchQuery(q = "gra", type = PlayerType.GOOGLE, size = 10))) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, search.status)
        val page = search.shared<PlayerSearchPageDto>()
        assertEquals(listOf(grace), page.items.map { it.id })
        assertEquals(listOf(OAuthProvider.GOOGLE), page.items.single().providers)
        assertNull(page.nextCursor)

        val paged = client.get(searchUrl(PlayerSearchQuery(size = 1))) { withSession(boss) }.shared<PlayerSearchPageDto>()
        assertEquals(1, paged.items.size)
        val next = client.get(searchUrl(PlayerSearchQuery(size = 1, cursor = paged.nextCursor))) { withSession(boss) }.shared<PlayerSearchPageDto>()
        assertEquals(listOf(grace), next.items.map { it.id })

        val detail = client.get(AdminRoutes.player(grace)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, detail.status)
        assertTrue("google-subject-1" !in detail.bodyAsText())
        val dto = detail.shared<PlayerDetailDto>()
        assertEquals("Grace", dto.displayName)
        assertEquals(listOf("ru"), dto.languages.map { it.lang })
        assertEquals(1, dto.suggestionCounts.pending)

        val ended = client.post(AdminRoutes.playerEndSessions(grace)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, ended.status)
        assertEquals(EndSessionsResultDto(0), ended.shared<EndSessionsResultDto>())

        val blocked = client.put(AdminRoutes.playerSuggestionBlock(grace)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, blocked.status)
        assertEquals("boss", assertNotNull(blocked.shared<PlayerDetailDto>().suggestionBlock).blockedBy.username)
        val filtered = client.get(searchUrl(PlayerSearchQuery(blocked = true))) { withSession(boss) }.shared<PlayerSearchPageDto>()
        assertEquals(listOf(grace), filtered.items.map { it.id })

        val unblocked = client.delete(AdminRoutes.playerSuggestionBlock(grace)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, unblocked.status)
        assertNull(unblocked.shared<PlayerDetailDto>().suggestionBlock)

        val cleared = client.delete(AdminRoutes.playerDisplayName(grace)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertNull(cleared.shared<PlayerDetailDto>().displayName)

        val deleted = client.post(AdminRoutes.playerDelete(grace)) { withSession(boss); jsonBody(DeletePlayerRequest(grace)) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get(AdminRoutes.player(grace)) { withSession(boss) }.status)

        assertEquals(
            listOf(
                AuditActions.PLAYER_SESSIONS_ENDED, AuditActions.PLAYER_SUGGESTIONS_BLOCKED, AuditActions.PLAYER_SUGGESTIONS_UNBLOCKED,
                AuditActions.PLAYER_DISPLAY_NAME_CLEARED, AuditActions.PLAYER_DELETED,
            ),
            auditRows().map { it[StaffAuditLogTable.action] }.filter { it.startsWith(AuditActions.PLAYER_PREFIX) },
        )
    }

    @Test
    fun malformedSearchesAndMismatchedConfirmationsAreValidationErrors() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val grace = insertPlayer("Grace")
        val client = adminClient()
        val boss = client.signIn("boss")

        val tooShort = client.get("${AdminRoutes.PLAYERS}?${PlayerParams.Q}=g") { withSession(boss) }
        assertEquals(HttpStatusCode.UnprocessableEntity, tooShort.status)
        assertEquals(FieldError(PlayerParams.Q, PlayerReasons.TOO_SHORT).toMessage(), tooShort.error().message)
        for (bad in listOf("type=facebook", "createdFrom=2026-13-01", "blocked=maybe", "size=0", "size=500", "cursor=%21%21")) {
            val response = client.get("${AdminRoutes.PLAYERS}?$bad") { withSession(boss) }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, bad)
            assertEquals(AdminErrors.VALIDATION_FAILED, response.error().error, bad)
        }

        val mismatch = client.post(AdminRoutes.playerDelete(grace)) { withSession(boss); jsonBody(DeletePlayerRequest(grace.uppercase())) }
        assertEquals(HttpStatusCode.UnprocessableEntity, mismatch.status)
        assertEquals(FieldError("confirmAccountId", PlayerReasons.MISMATCH).toMessage(), mismatch.error().message)
        assertNotNull(userRow(grace))
        assertTrue(auditRows().none { it[StaffAuditLogTable.action] == AuditActions.PLAYER_DELETED })
    }

    @Test
    fun anUnknownAccountIsNotFoundOnEveryAccountRoute() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        for ((name, send) in routes(unknown).drop(1)) {
            val response = client.send { withSession(boss) }
            assertEquals(HttpStatusCode.NotFound, response.status, name)
            assertEquals(AdminErrors.NOT_FOUND, response.error().error, name)
        }
        assertTrue(auditRows().none { it[StaffAuditLogTable.action].startsWith(AuditActions.PLAYER_PREFIX) })
    }

    @Test
    fun aWorderIsRefusedEveryRouteAndNothingChanges() = testApplication {
        insertStaff("maria", Role.WORDER, listOf("en"))
        val grace = insertPlayer("Grace")
        insertStats(grace, listOf(ResultRecordDto("en", 20_000, true, 3)))
        val client = adminClient()
        val worder = client.signIn("maria")
        val before = userRow(grace)!!

        for ((name, send) in routes(grace)) {
            val response = client.send { withSession(worder) }
            assertEquals(HttpStatusCode.Forbidden, response.status, name)
            assertEquals(AdminErrors.FORBIDDEN, response.error().error, name)
        }

        val after = userRow(grace)!!
        assertEquals(before[UsersTable.name], after[UsersTable.name])
        assertNull(after[UsersTable.suggestionsBlockedAt])
        assertTrue(auditRows().none { it[StaffAuditLogTable.action].startsWith(AuditActions.PLAYER_PREFIX) })
    }

    @Test
    fun withoutAStaffSessionEveryRouteIsUnauthorizedEvenWithAPlayerToken() = testApplication {
        val client = adminClient()
        val player = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        for ((name, send) in routes(player.userId)) {
            assertEquals(HttpStatusCode.Unauthorized, client.send {}.status, "$name without a session")
            val withToken = client.send { header(HttpHeaders.Authorization, "Bearer ${player.tokens.accessToken}") }
            assertEquals(HttpStatusCode.Unauthorized, withToken.status, "$name with a player token")
        }
        assertNotNull(userRow(player.userId))
        assertEquals(1, transaction(DatabaseFactory.init()) { UsersTable.selectAll().count() })
    }

    @Test
    fun aMutationWithoutTheAntiForgeryHeaderIsForbidden() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val grace = insertPlayer("Grace")
        val client = adminClient()
        val boss = client.signIn("boss")

        for ((name, send) in routes(grace).drop(2)) {
            val response = client.send { withSession(boss, xsrf = null) }
            assertEquals(HttpStatusCode.Forbidden, response.status, name)
        }
        val row = userRow(grace)!!
        assertEquals("Grace", row[UsersTable.name])
        assertNull(row[UsersTable.suggestionsBlockedAt])
        assertTrue(auditRows().none { it[StaffAuditLogTable.action].startsWith(AuditActions.PLAYER_PREFIX) })
        // Reads need no anti-forgery header.
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.player(grace)) { withSession(boss, xsrf = null) }.status)
        assertEquals(adminJson.decodeFromString<PlayerDetailDto>(client.get(AdminRoutes.player(grace)) { withSession(boss) }.bodyAsText()).id, grace)
    }
}
