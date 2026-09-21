package uz.abumme.harfgame.backend.admin

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.auth.StaffBootstrap
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiRoutes
import io.ktor.client.call.body
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaffBootstrapTest {
    private val bootstrapPassword = "bootstrap password 1"

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private fun bootstrap(admin: AdminBackend = adminBackend()) = admin.bootstrap

    private fun staffCount() = transaction(DatabaseFactory.init()) { StaffTable.selectAll().count() }

    @Test
    fun emptySystemGetsItsFirstAdmin() = testApplication {
        val admin = adminBackend()
        assertEquals(StaffBootstrap.Outcome.CREATED, runBlocking { admin.bootstrap.run("Root", bootstrapPassword) })

        val client = adminClient(admin)
        val login = client.login("root", bootstrapPassword)
        assertEquals(HttpStatusCode.OK, login.status)
        assertEquals(Role.ADMIN, login.body<MeDto>().role)

        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.STAFF_BOOTSTRAPPED }
        assertEquals("SYSTEM", entry[StaffAuditLogTable.actorKind])
        assertNull(entry[StaffAuditLogTable.actorStaffId])
        assertEquals(login.body<MeDto>().id, entry[StaffAuditLogTable.targetId])
    }

    @Test
    fun disabledWorderIsRecoveredAsActiveAdmin() = testApplication {
        val id = insertStaff("dilnoza", Role.WORDER, status = StaffStatus.DISABLED)
        insertStaff("olim", Role.ADMIN, status = StaffStatus.DISABLED)
        transaction(DatabaseFactory.init()) {
            StaffTable.update({ StaffTable.id eq id }) {
                it[failedLoginCount] = 3
                it[lockedUntil] = Instant.now().plusSeconds(600)
            }
        }
        val admin = adminBackend()
        val oldSession = transaction(DatabaseFactory.init()) { admin.sessions.create(id, null, null) }

        assertEquals(StaffBootstrap.Outcome.RECOVERED, runBlocking { admin.bootstrap.run("dilnoza", bootstrapPassword) })

        val row = staffRow(id)
        assertEquals(Role.ADMIN.name, row[StaffTable.role])
        assertEquals(StaffStatus.ACTIVE.name, row[StaffTable.status])
        assertNull(row[StaffTable.lockedUntil])
        assertEquals(0, row[StaffTable.failedLoginCount])
        assertNull(runBlocking { admin.sessions.resolve(oldSession.token) }, "old sessions end")

        val client = adminClient(admin)
        assertEquals(HttpStatusCode.Unauthorized, client.login("dilnoza", PASSWORD).status)
        assertEquals(Role.ADMIN, client.login("dilnoza", bootstrapPassword).body<MeDto>().role)
        assertEquals("SYSTEM", auditRows().single { it[StaffAuditLogTable.action] == AuditActions.STAFF_BOOTSTRAPPED }[StaffAuditLogTable.actorKind])
    }

    @Test
    fun activeAdminMakesBootstrapANoOp() {
        val bossId = insertStaff("boss", Role.ADMIN)
        val dilnozaId = insertStaff("dilnoza", Role.WORDER, status = StaffStatus.DISABLED)
        val before = listOf(staffRow(bossId), staffRow(dilnozaId))

        assertEquals(StaffBootstrap.Outcome.ACTIVE_ADMIN_EXISTS, runBlocking { bootstrap().run("dilnoza", bootstrapPassword) })
        assertEquals(StaffBootstrap.Outcome.ACTIVE_ADMIN_EXISTS, runBlocking { bootstrap().run("newcomer", bootstrapPassword) })

        assertEquals(2, staffCount())
        assertEquals(before.map { it[StaffTable.passwordHash] to it[StaffTable.status] }, listOf(staffRow(bossId), staffRow(dilnozaId)).map { it[StaffTable.passwordHash] to it[StaffTable.status] })
        assertEquals(0, auditRows().size)
    }

    @Test
    fun invalidBootstrapConfigurationIsSkippedAndTheServerStillServes() = testApplication {
        val admin = adminBackend()

        assertEquals(StaffBootstrap.Outcome.INVALID_CONFIGURATION, runBlocking { admin.bootstrap.run("root", "x".repeat(11)) })
        assertEquals(StaffBootstrap.Outcome.INVALID_CONFIGURATION, runBlocking { admin.bootstrap.run("no spaces allowed", bootstrapPassword) })
        assertEquals(StaffBootstrap.Outcome.NOT_CONFIGURED, runBlocking { admin.bootstrap.run(null, null) })
        assertEquals(StaffBootstrap.Outcome.NOT_CONFIGURED, runBlocking { admin.bootstrap.run("root", "") })
        assertEquals(0, staffCount())
        assertEquals(0, auditRows().size)

        val client = adminClient(admin)
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.wordpack("en")).status, "the player API serves")
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME).status)
    }
}
