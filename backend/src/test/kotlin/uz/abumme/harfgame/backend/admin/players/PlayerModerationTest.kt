package uz.abumme.harfgame.backend.admin.players

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.FixedOAuthVerifier
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.statusOf
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ending sessions, blocking and unblocking suggestions, and clearing a display name. */
class PlayerModerationTest {

    private val clock = MutableClock()
    private val auth = AuthServerService(JwtService(), verifiers = mapOf(OAuthProvider.GOOGLE to FixedOAuthVerifier(OAuthProvider.GOOGLE)))
    private val service = PlayersService(clock, AuditLog(clock), auth)
    private lateinit var boss: AuditActor.Staff
    private lateinit var deputy: AuditActor.Staff

    @BeforeTest
    fun setup() {
        resetPlayerData()
        boss = AuditActor.Staff(insertStaff("boss", Role.ADMIN))
        deputy = AuditActor.Staff(insertStaff("deputy", Role.ADMIN))
    }

    private fun actions() = auditRows().map { it[StaffAuditLogTable.action] }

    // --- ending sessions ---------------------------------------------------------------------------------------------

    @Test
    fun endingSessionsRejectsEveryRefreshTokenAndLeavesTheAccountAlone() {
        val (id, tokens) = runBlocking {
            val anon = auth.createAnonymousAccount()
            val rotated = auth.refreshToken(anon.tokens.refreshToken)!! // the first token is now rotated
            val linked = auth.linkAccount(anon.userId, LinkAccountRequest(OAuthProvider.GOOGLE, "google-subject-1", displayName = "Grace"))!!
            anon.userId to listOf(anon.tokens.refreshToken, rotated.tokens.refreshToken, linked.tokens.refreshToken)
        }
        insertStats(id, listOf(ResultRecordDto("en", 20_000, true, 3)))
        val suggestion = insertSuggestion("en", "crane", author = id)
        assertEquals(2, runBlocking { service.detail(id) }!!.activeSessions)

        val result = runBlocking { service.endSessions(id, boss) }

        assertEquals(3, result.revoked)
        for (token in tokens) assertNull(runBlocking { auth.refreshToken(token) }, "refresh with a previous token")
        val detail = runBlocking { service.detail(id) }!!
        assertEquals(0, detail.activeSessions)
        assertEquals("Grace", detail.displayName)
        assertEquals(listOf(OAuthProvider.GOOGLE), detail.providers)
        assertEquals(1, detail.languages.size)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(suggestion))
        transaction(DatabaseFactory.init()) {
            assertEquals(1, OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.userId eq id }.count())
            assertEquals(1, UserStatsTable.selectAll().where { UserStatsTable.userId eq id }.count())
            assertEquals(id, WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestion }.single()[WordSuggestionsTable.suggestedBy])
        }

        val entry = auditRows().single()
        assertEquals(AuditActions.PLAYER_SESSIONS_ENDED, entry[StaffAuditLogTable.action])
        assertEquals(id, entry[StaffAuditLogTable.targetId])
        assertEquals("""{"revoked":3}""", entry[StaffAuditLogTable.details])
    }

    @Test
    fun endingTheSessionsOfAnUnknownAccountIsNotFoundAndUnaudited() {
        val refused = assertFailsWith<AdminApiException> { runBlocking { service.endSessions("3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f", boss) } }
        assertEquals(HttpStatusCode.NotFound, refused.status)
        assertTrue(auditRows().isEmpty())
    }

    // --- blocking ----------------------------------------------------------------------------------------------------

    @Test
    fun aSecondBlockKeepsTheOriginalWhoAndWhenAndIsAuditedOnce() {
        val id = insertPlayer("Grace")
        val blockedAt = clock.now

        val first = runBlocking { service.blockSuggestions(id, boss) }
        clock.advance(Duration.ofHours(2))
        val second = runBlocking { service.blockSuggestions(id, deputy) }

        assertEquals(blockedAt.toEpochMilli(), first.suggestionBlock!!.blockedAt)
        assertEquals("boss", first.suggestionBlock!!.blockedBy.username)
        assertEquals(first.suggestionBlock, second.suggestionBlock)
        assertEquals(boss.staffId, userRow(id)!![UsersTable.suggestionsBlockedBy])
        assertEquals(listOf(AuditActions.PLAYER_SUGGESTIONS_BLOCKED), actions())
        assertEquals(boss.staffId, auditRows().single()[StaffAuditLogTable.actorStaffId])
    }

    @Test
    fun unblockClearsBothColumnsOnce() {
        val id = insertPlayer("Grace")
        runBlocking { service.blockSuggestions(id, boss) }

        val unblocked = runBlocking { service.unblockSuggestions(id, deputy) }
        runBlocking { service.unblockSuggestions(id, deputy) }

        assertNull(unblocked.suggestionBlock)
        assertNull(userRow(id)!![UsersTable.suggestionsBlockedAt])
        assertNull(userRow(id)!![UsersTable.suggestionsBlockedBy])
        assertEquals(listOf(AuditActions.PLAYER_SUGGESTIONS_BLOCKED, AuditActions.PLAYER_SUGGESTIONS_UNBLOCKED), actions())
    }

    @Test
    fun pendingSuggestionsStayPendingAfterABlock() {
        val id = insertPlayer("Grace")
        val pending = insertSuggestion("en", "crane", author = id)
        val decided = insertSuggestion("en", "quilt", SuggestionStatus.ACCEPTED, decidedAt = clock.now, author = id)

        runBlocking { service.blockSuggestions(id, boss) }

        assertEquals(SuggestionStatus.PENDING.name, statusOf(pending))
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(decided))
        assertEquals(1, runBlocking { service.detail(id) }!!.suggestionCounts.pending)
    }

    // --- display name ------------------------------------------------------------------------------------------------

    @Test
    fun clearingANameMakesTheAuthorAnonymousAndKeepsTheNameNowhere() {
        val id = insertPlayer("Grace Hopper")
        val suggestions = SuggestionServerService(WordPackServerService())
        assertEquals("Grace Hopper", runBlocking { suggestions.authorLabel(id) })

        val cleared = runBlocking { service.clearDisplayName(id, boss) }
        runBlocking { service.clearDisplayName(id, boss) }

        assertNull(cleared.displayName)
        assertNull(userRow(id)!![UsersTable.name])
        assertEquals("Аноним", runBlocking { suggestions.authorLabel(id) })
        assertEquals(emptyList(), runBlocking { service.search(PlayerSearchQuery(q = "Grace")) }.items)
        val entry = auditRows().single()
        assertEquals(AuditActions.PLAYER_DISPLAY_NAME_CLEARED, entry[StaffAuditLogTable.action])
        assertEquals(id, entry[StaffAuditLogTable.targetId])
        assertFalse(entry[StaffAuditLogTable.details].orEmpty().contains("Grace"))
        assertTrue(auditRows().none { it[StaffAuditLogTable.details].orEmpty().contains("Grace") })
    }

    @Test
    fun actionsOnAnUnknownAccountAreNotFound() {
        val unknown = "3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"
        for (action in listOf<suspend () -> Unit>(
            { service.blockSuggestions(unknown, boss) },
            { service.unblockSuggestions(unknown, boss) },
            { service.clearDisplayName(unknown, boss) },
        )) {
            assertEquals(HttpStatusCode.NotFound, assertFailsWith<AdminApiException> { runBlocking { action() } }.status)
        }
        assertTrue(auditRows().isEmpty())
        assertNotNull(boss.staffId)
    }
}
