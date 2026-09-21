package uz.abumme.harfgame.backend.admin

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.auth.StaffSessionStore
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.security.TokenUtils
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffSessionStoreTest {
    private val clock = MutableClock()
    private val store = StaffSessionStore(clock)
    private lateinit var staffId: String

    @BeforeTest
    fun setup() {
        resetAdminData()
        staffId = insertStaff("aziz", Role.WORDER, languages = listOf("ru", "kk"))
    }

    private fun create(): StaffSessionStore.NewSession =
        transaction(DatabaseFactory.init()) { store.create(staffId, "203.0.113.7", "test-agent") }

    private fun lastSeen(sessionId: String) = transaction(DatabaseFactory.init()) {
        StaffSessionsTable.selectAll().where { StaffSessionsTable.id eq sessionId }.single()[StaffSessionsTable.lastSeenAt]
    }

    @Test
    fun createdSessionStoresOnlyTheHashAndResolvesToThePrincipal() = runBlocking {
        val session = create()

        val row = transaction(DatabaseFactory.init()) {
            StaffSessionsTable.selectAll().where { StaffSessionsTable.id eq session.id }.single()
        }
        assertEquals(TokenUtils.hashToken(session.token), row[StaffSessionsTable.tokenHash])
        assertNotEquals(session.token, row[StaffSessionsTable.tokenHash])
        assertEquals(43, session.token.length, "256-bit token, base64url")
        assertEquals(clock.now.plus(Duration.ofDays(7)), row[StaffSessionsTable.expiresAt])

        val principal = assertNotNull(store.resolve(session.token))
        assertEquals(staffId, principal.staffId)
        assertEquals("aziz", principal.username)
        assertEquals(Role.WORDER, principal.role)
        assertEquals(setOf("ru", "kk"), principal.assignedLanguages)
        assertEquals(session.id, principal.sessionId)

        assertNull(store.resolve("not-a-token"))
    }

    @Test
    fun idleTimeoutEndsTheSessionAfterTwelveHours() = runBlocking {
        val session = create()

        clock.advance(Duration.ofHours(12))
        assertNotNull(store.resolve(session.token), "exactly 12 h idle is still alive")

        val other = create()
        clock.advance(Duration.ofHours(12).plusMillis(1))
        assertNull(store.resolve(other.token), "more than 12 h idle has ended")
    }

    @Test
    fun activityKeepsTheSessionAliveButNotBeyondSevenDays() = runBlocking {
        val session = create()

        // A request every 4 hours for 2 days.
        repeat(12) {
            clock.advance(Duration.ofHours(4))
            assertNotNull(store.resolve(session.token), "request ${it + 1}")
        }

        // Keep it busy until just before the absolute limit, then reach it: still in use, but 7 days have passed
        // since sign-in.
        while (clock.now.plus(Duration.ofHours(4)).isBefore(session.expiresAt)) {
            clock.advance(Duration.ofHours(4))
            assertNotNull(store.resolve(session.token))
        }
        clock.now = session.expiresAt.minusMillis(1)
        assertNotNull(store.resolve(session.token))
        clock.advance(Duration.ofMillis(1))
        assertNull(store.resolve(session.token))
    }

    @Test
    fun lastSeenIsWrittenAtMostOnceAMinute() = runBlocking {
        val session = create()
        val created = lastSeen(session.id)

        clock.advance(Duration.ofSeconds(30))
        assertNotNull(store.resolve(session.token))
        assertEquals(created, lastSeen(session.id), "no write within the first minute")

        clock.advance(Duration.ofSeconds(30))
        assertNotNull(store.resolve(session.token))
        assertEquals(clock.now, lastSeen(session.id), "written once a minute has passed")

        clock.advance(Duration.ofSeconds(59))
        assertNotNull(store.resolve(session.token))
        assertEquals(clock.now.minusSeconds(59), lastSeen(session.id))
    }

    @Test
    fun revokedSessionsAndDisabledStaffDoNotResolve() = runBlocking {
        val first = create()
        val second = create()
        val third = create()

        transaction(DatabaseFactory.init()) { store.revoke(first.id) }
        assertNull(store.resolve(first.token))
        assertNotNull(store.resolve(second.token))

        transaction(DatabaseFactory.init()) { store.revokeAllExcept(staffId, keepSessionId = third.id) }
        assertNull(store.resolve(second.token))
        assertNotNull(store.resolve(third.token))

        transaction(DatabaseFactory.init()) {
            StaffTable.update({ StaffTable.id eq staffId }) { it[status] = StaffStatus.DISABLED.name }
        }
        assertNull(store.resolve(third.token), "a disabled member's session no longer works")

        transaction(DatabaseFactory.init()) {
            StaffTable.update({ StaffTable.id eq staffId }) { it[status] = StaffStatus.ACTIVE.name }
            store.revokeAll(staffId)
        }
        assertNull(store.resolve(third.token))
    }

    @Test
    fun cleanupDeletesOnlySessionsExpiredOrRevokedMoreThanSevenDaysAgo() = runBlocking {
        val start = clock.now
        val expiredLongAgo = create() // expires start + 7 d
        clock.now = start.plus(Duration.ofDays(1))
        val revokedLongAgo = create()
        transaction(DatabaseFactory.init()) { store.revoke(revokedLongAgo.id) } // revoked start + 1 d
        clock.now = start.plus(Duration.ofDays(7))
        val revokedRecently = create() // expires start + 14 d
        clock.now = start.plus(Duration.ofDays(8))
        transaction(DatabaseFactory.init()) { store.revoke(revokedRecently.id) } // revoked start + 8 d
        clock.now = start.plus(Duration.ofDays(9))
        val live = create() // expires start + 16 d

        clock.now = start.plus(Duration.ofDays(14)).plusSeconds(1)
        assertEquals(2, store.deleteStale())

        val remaining = transaction(DatabaseFactory.init()) {
            StaffSessionsTable.selectAll().map { it[StaffSessionsTable.id] }.toSet()
        }
        assertFalse(expiredLongAgo.id in remaining)
        assertFalse(revokedLongAgo.id in remaining)
        assertTrue(revokedRecently.id in remaining)
        assertTrue(live.id in remaining)
    }
}
