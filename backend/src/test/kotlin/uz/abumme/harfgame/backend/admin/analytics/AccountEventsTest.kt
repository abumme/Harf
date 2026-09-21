package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.FixedOAuthVerifier
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.players.PlayersService
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.OAuthProvider
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountEventsTest {
    // 00:30 on 2026-09-17 in Tashkent, still 2026-09-16 in UTC and Moscow.
    private val clock = MutableClock(Instant.parse("2026-09-16T19:30:00Z"))
    private val tashkentDate = LocalDate.parse("2026-09-17")
    private val auth = AuthServerService(
        JwtService(),
        verifiers = mapOf(
            OAuthProvider.GOOGLE to FixedOAuthVerifier(OAuthProvider.GOOGLE),
            OAuthProvider.APPLE to FixedOAuthVerifier(OAuthProvider.APPLE),
        ),
        clock = clock,
    )

    @BeforeTest
    fun setup() {
        resetAnalyticsData()
    }

    private fun identities(userId: String) = transaction(DatabaseFactory.init()) {
        OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.userId eq userId }.toList()
    }

    @Test
    fun aPlayersOwnDeletionCountsOnceOnTheTashkentDateWithoutIdentity() = runBlocking<Unit> {
        val first = auth.createAnonymousAccount()
        val second = auth.createAnonymousAccount()

        assertTrue(auth.deleteAccount(first.userId))
        assertEquals(1, deletionsOn(tashkentDate))
        assertTrue(auth.deleteAccount(second.userId))
        assertEquals(2, deletionsOn(tashkentDate))
        assertNull(deletionsOn(tashkentDate.minusDays(1)))
    }

    @Test
    fun anAdminDeletionGoesThroughTheSamePathAndCounts() = runBlocking<Unit> {
        val boss = insertStaff("boss", Role.ADMIN)
        val players = PlayersService(clock, AuditLog(clock), auth)
        val player = auth.createAnonymousAccount()

        players.delete(player.userId, player.userId, AuditActor.Staff(boss))

        assertEquals(1, deletionsOn(tashkentDate))
    }

    @Test
    fun aDeletionThatFindsNothingOrFailsIsNotCounted() = runBlocking<Unit> {
        assertFalse(auth.deleteAccount("no-such-account"))
        assertNull(deletionsOn(tashkentDate))

        // A deletion whose transaction fails (here: its in-transaction hook throws) rolls the count back with it.
        val player = auth.createAnonymousAccount()
        assertFailsWith<IllegalStateException> { auth.deleteAccount(player.userId) { throw IllegalStateException("audit failed") } }
        assertNull(deletionsOn(tashkentDate))
        assertNotNull(transaction(DatabaseFactory.init()) { UsersTable.selectAll().where { UsersTable.id eq player.userId }.singleOrNull() })
    }

    @Test
    fun mergeLinkDiscardingAnAnonymousAccountIsNotADeletion() = runBlocking<Unit> {
        val owner = auth.createAnonymousAccount()
        auth.linkAccount(owner.userId, LinkAccountRequest(OAuthProvider.GOOGLE, "google-merge-subject"))!!
        val orphan = auth.createAnonymousAccount()

        val merged = auth.linkAccount(orphan.userId, LinkAccountRequest(OAuthProvider.GOOGLE, "google-merge-subject"))!!

        assertEquals(owner.userId, merged.userId)
        assertNull(transaction(DatabaseFactory.init()) { UsersTable.selectAll().where { UsersTable.id eq orphan.userId }.singleOrNull() })
        assertNull(deletionsOn(tashkentDate))
    }

    @Test
    fun aNewLinkRecordsItsTimeAndReLinkingAnOwnedIdentityInsertsNothing() = runBlocking<Unit> {
        val player = auth.createAnonymousAccount()
        auth.linkAccount(player.userId, LinkAccountRequest(OAuthProvider.APPLE, "apple-link-subject"))!!
        val linked = identities(player.userId).single()
        assertEquals(clock.now, linked[OAuthIdentitiesTable.linkedAt])

        clock.advance(java.time.Duration.ofHours(2))
        auth.linkAccount(player.userId, LinkAccountRequest(OAuthProvider.APPLE, "apple-link-subject"))!!

        val after = identities(player.userId).single()
        assertEquals(linked[OAuthIdentitiesTable.id], after[OAuthIdentitiesTable.id])
        assertEquals(Instant.parse("2026-09-16T19:30:00Z"), after[OAuthIdentitiesTable.linkedAt])
    }
}
