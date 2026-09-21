package uz.abumme.harfgame.backend.admin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.access.permissions
import uz.abumme.harfgame.backend.admin.auth.STAFF_SESSION_AUTH
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.module
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import java.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessControlTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    /**
     * The real module plus two language-specific probe routes, standing in for the word routes of later changes:
     * a read and a write that records an audit entry only after its language check passed.
     */
    private fun ApplicationTestBuilder.probeClient(): HttpClient {
        val admin = adminBackend()
        val audit = AuditLog(Clock.systemUTC())
        application {
            module(admin = admin)
            routing {
                authenticate(STAFF_SESSION_AUTH) {
                    get("/probe/{lang}") {
                        call.staffPrincipal().requireLanguage(call.parameters["lang"]!!)
                        call.respond(HttpStatusCode.OK, "read")
                    }
                    post("/probe/{lang}") {
                        val principal = call.staffPrincipal()
                        val lang = call.parameters["lang"]!!
                        DatabaseFactory.dbQuery {
                            principal.requireLanguage(lang)
                            audit.record(AuditActor.Staff(principal.staffId), "PROBE_WRITE", lang = lang)
                        }
                        call.respond(HttpStatusCode.OK, "written")
                    }
                }
            }
        }
        return createClient { install(ContentNegotiation) { json(adminJson) } }
    }

    @Test
    fun adminReachesStaffManagementAuditAndEveryLanguage() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = probeClient()
        val session = client.signIn("boss")

        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.STAFF) { withSession(session) }.status)
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUDIT) { withSession(session) }.status)
        for (lang in PACK_LANGUAGES) {
            assertEquals(HttpStatusCode.OK, client.get("/probe/$lang") { withSession(session) }.status, lang)
        }
    }

    @Test
    fun worderIsRefusedStaffManagementAndChangesNothing() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        insertStaff("aziz", Role.WORDER, languages = listOf("ru"))
        val client = probeClient()
        val session = client.signIn("aziz")
        val before = staffRow(bossId)

        assertEquals(HttpStatusCode.Forbidden, client.get(AdminRoutes.STAFF) { withSession(session) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get(AdminRoutes.staff(bossId)) { withSession(session) }.status)
        val create = client.post(AdminRoutes.STAFF) {
            withSession(session)
            jsonBody(CreateStaffRequest("intruder", PASSWORD, Role.ADMIN))
        }
        assertEquals(HttpStatusCode.Forbidden, create.status)
        assertEquals("forbidden", create.error().error)
        val demote = client.patch(AdminRoutes.staff(bossId)) {
            withSession(session)
            jsonBody(UpdateStaffRequest(role = Patch.Set(Role.WORDER)))
        }
        assertEquals(HttpStatusCode.Forbidden, demote.status)
        assertEquals(HttpStatusCode.Forbidden, client.post(AdminRoutes.staffDisable(bossId)) { withSession(session) }.status)

        assertEquals(2L, transaction(DatabaseFactory.init()) { StaffTable.selectAll().count() })
        assertEquals(before[StaffTable.role], staffRow(bossId)[StaffTable.role])
        assertEquals(before[StaffTable.status], staffRow(bossId)[StaffTable.status])
    }

    @Test
    fun worderIsLimitedToAssignedLanguagesEvenWhenCallingTheApiDirectly() = testApplication {
        insertStaff("aziz", Role.WORDER, languages = listOf("ru"))
        val client = probeClient()
        val session = client.signIn("aziz")

        assertEquals(HttpStatusCode.OK, client.get("/probe/ru") { withSession(session) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/probe/ru") { withSession(session) }.status)

        val readKk = client.get("/probe/kk") { withSession(session) }
        assertEquals(HttpStatusCode.Forbidden, readKk.status)
        assertEquals("forbidden", readKk.error().error)
        assertEquals(HttpStatusCode.Forbidden, client.post("/probe/kk") { withSession(session) }.status)

        val probeWrites = auditRows().filter { it[StaffAuditLogTable.action] == "PROBE_WRITE" }
        assertEquals(listOf("ru"), probeWrites.map { it[StaffAuditLogTable.lang] }, "the refused write changed nothing")
    }

    @Test
    fun removedLanguageDemotionAndDisableApplyOnTheNextRequest() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val deputyId = insertStaff("deputy", Role.ADMIN)
        val azizId = insertStaff("aziz", Role.WORDER, languages = listOf("ru", "kk"))
        val client = probeClient()
        val boss = client.signIn("boss")
        val deputy = client.signIn("deputy")
        val aziz = client.signIn("aziz")

        assertEquals(HttpStatusCode.OK, client.get("/probe/kk") { withSession(aziz) }.status)
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.STAFF) { withSession(deputy) }.status)

        // Remove kk: refused on Aziz's very next request, without signing in again.
        val languages = client.patch(AdminRoutes.staff(azizId)) {
            withSession(boss)
            jsonBody(UpdateStaffRequest(languages = Patch.Set(listOf("ru"))))
        }
        assertEquals(HttpStatusCode.OK, languages.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/probe/kk") { withSession(aziz) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/probe/ru") { withSession(aziz) }.status)

        // Demote the deputy: staff management is refused on their next request.
        val demote = client.patch(AdminRoutes.staff(deputyId)) {
            withSession(boss)
            jsonBody(UpdateStaffRequest(role = Patch.Set(Role.WORDER), languages = Patch.Set(listOf("en"))))
        }
        assertEquals(HttpStatusCode.OK, demote.status)
        assertEquals(HttpStatusCode.Forbidden, client.get(AdminRoutes.STAFF) { withSession(deputy) }.status)

        // Disable Aziz: the next request is unauthenticated.
        assertEquals(HttpStatusCode.OK, client.post(AdminRoutes.staffDisable(azizId)) { withSession(boss) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/probe/ru") { withSession(aziz) }.status)
    }

    @Test
    fun worderAskingForTheWholeAuditLogIsForbidden() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        insertStaff("aziz", Role.WORDER)
        val client = probeClient()
        val session = client.signIn("aziz")

        assertEquals(HttpStatusCode.Forbidden, client.get("${AdminRoutes.AUDIT}?actor=$bossId") { withSession(session) }.status)
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUDIT) { withSession(session) }.status)
    }

    @Test
    fun onlyAnAdminAdministersPlayerAccounts() {
        val players = setOf(Permission.PLAYERS_READ, Permission.PLAYERS_WRITE)
        assertTrue(Role.ADMIN.permissions.containsAll(players))
        assertTrue(Role.WORDER.permissions.none { it in players })
        val worder = StaffPrincipal("s", "aziz", null, Role.WORDER, setOf("ru"), "session")
        assertFalse(worder.has(Permission.PLAYERS_READ))
        assertFalse(worder.has(Permission.PLAYERS_WRITE))
    }

    @Test
    fun principalLanguageRules() {
        val worder = StaffPrincipal("s", "aziz", null, Role.WORDER, setOf("uz-latn", "uz-cyrl"), "session")
        val admin = worder.copy(role = Role.ADMIN, assignedLanguages = emptySet())

        assertEquals(listOf("uz-cyrl", "uz-latn"), worder.languagesWithin(PACK_LANGUAGES))
        assertEquals(PACK_LANGUAGES, admin.languagesWithin(PACK_LANGUAGES))
        worder.requireLanguage("uz-latn")
        admin.requireLanguage("kk")
        val refused = assertFailsWith<AdminApiException> { worder.requireLanguage("kk") }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertTrue(admin.canUseLanguage("anything"))
    }
}
