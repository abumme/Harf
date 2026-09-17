package uz.abumme.harfgame.backend.admin.players

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.FixedOAuthVerifier
import uz.abumme.harfgame.backend.RecordingAppleRevoker
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.RefreshTokensTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.backend.suggestionColumn
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.sync.ResultRecordDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerDeleteTest {

    private val clock = MutableClock()
    private val revoker = RecordingAppleRevoker()
    private val auth = AuthServerService(
        JwtService(),
        verifiers = mapOf(
            OAuthProvider.APPLE to FixedOAuthVerifier(OAuthProvider.APPLE),
            OAuthProvider.GOOGLE to FixedOAuthVerifier(OAuthProvider.GOOGLE),
        ),
        appleRevoker = revoker,
    )
    private val service = PlayersService(clock, AuditLog(clock), auth)
    private lateinit var boss: AuditActor.Staff

    @BeforeTest
    fun setup() {
        resetPlayerData()
        boss = AuditActor.Staff(insertStaff("boss", Role.ADMIN))
    }

    private class Player(val id: String, val refreshTokens: List<String>, val suggestionId: String)

    /** A player signed in with Apple and Google (the provider subjects are the id tokens), with stats and a suggestion. */
    private fun linkedPlayer(): Player = runBlocking {
        val anon = auth.createAnonymousAccount()
        val apple = auth.linkAccount(anon.userId, LinkAccountRequest(OAuthProvider.APPLE, "apple-subject-1", displayName = "Grace"))!!
        val google = auth.linkAccount(anon.userId, LinkAccountRequest(OAuthProvider.GOOGLE, "google-subject-1"))!!
        insertStats(anon.userId, listOf(ResultRecordDto("en", 20_000, true, 3)))
        val suggestion = insertSuggestion("en", "crane", author = anon.userId)
        Player(anon.userId, listOf(anon.tokens.refreshToken, apple.tokens.refreshToken, google.tokens.refreshToken), suggestion)
    }

    private fun countFor(id: String) = transaction(DatabaseFactory.init()) {
        Triple(
            OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.userId eq id }.count(),
            RefreshTokensTable.selectAll().where { RefreshTokensTable.userId eq id }.count(),
            UserStatsTable.selectAll().where { UserStatsTable.userId eq id }.count(),
        )
    }

    @Test
    fun deletingRemovesTheAccountLikeThePlayersOwnDeletion() {
        val player = linkedPlayer()
        assertEquals(Triple(2L, 3L, 1L), countFor(player.id))

        runBlocking { service.delete(player.id, player.id, boss) }

        assertNull(userRow(player.id))
        assertEquals(Triple(0L, 0L, 0L), countFor(player.id))
        assertEquals(listOf("apple-subject-1"), revoker.revoked, "the linked Apple identity is revoked")
        // The suggestion outlives the account, without an author.
        assertNull(suggestionColumn(player.suggestionId, WordSuggestionsTable.suggestedBy))
        assertEquals(SuggestionStatus.PENDING.name, suggestionColumn(player.suggestionId, WordSuggestionsTable.status))
        // No refresh token of the account works any more, and a search for its id finds nothing.
        for (token in player.refreshTokens) assertNull(runBlocking { auth.refreshToken(token) })
        assertEquals(emptyList(), runBlocking { service.search(PlayerSearchQuery(q = player.id)) }.items)
    }

    @Test
    fun theDeletionIsAuditedWithProviderTypesOnly() {
        val player = linkedPlayer()

        runBlocking { service.delete(player.id, player.id, boss) }

        val entry = auditRows().single()
        assertEquals(AuditActions.PLAYER_DELETED, entry[StaffAuditLogTable.action])
        assertEquals(boss.staffId, entry[StaffAuditLogTable.actorStaffId])
        assertEquals(AuditTargets.PLAYER, entry[StaffAuditLogTable.targetType])
        assertEquals(player.id, entry[StaffAuditLogTable.targetId])
        val details = entry[StaffAuditLogTable.details]!!
        assertEquals("""{"providers":["APPLE","GOOGLE"]}""", details)
        for (secret in listOf("apple-subject-1", "google-subject-1", "Grace")) assertFalse(secret in details)
    }

    @Test
    fun aMismatchedConfirmationDeletesRevokesAndAuditsNothing() {
        val player = linkedPlayer()

        for (confirmation in listOf("", player.id.uppercase(), player.id.take(8), " ${player.id}")) {
            val refused = assertFailsWith<AdminApiException> { runBlocking { service.delete(player.id, confirmation, boss) } }
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            assertEquals(FieldError("confirmAccountId", PlayerReasons.MISMATCH).toMessage(), refused.message)
        }

        assertEquals(Triple(2L, 3L, 1L), countFor(player.id))
        assertTrue(revoker.revoked.isEmpty())
        assertTrue(auditRows().isEmpty())
        assertEquals(player.id, suggestionColumn(player.suggestionId, WordSuggestionsTable.suggestedBy))
    }

    @Test
    fun anUnknownAccountIsNotFoundBeforeAnythingIsRevoked() {
        val unknown = "3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"
        val refused = assertFailsWith<AdminApiException> { runBlocking { service.delete(unknown, unknown, boss) } }
        assertEquals(HttpStatusCode.NotFound, refused.status)
        assertTrue(revoker.revoked.isEmpty())
        assertTrue(auditRows().isEmpty())
    }
}
