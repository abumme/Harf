package uz.abumme.harfgame.backend.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.ResetPasswordRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffManagementIntegrationTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private suspend fun HttpClient.create(session: StaffSession, request: CreateStaffRequest) =
        post(AdminRoutes.STAFF) { withSession(session); jsonBody(request) }

    private suspend fun HttpClient.update(session: StaffSession, id: String, request: UpdateStaffRequest) =
        patch(AdminRoutes.staff(id)) { withSession(session); jsonBody(request) }

    private fun activeAdminCount() = transaction(DatabaseFactory.init()) {
        StaffTable.selectAll().where { (StaffTable.role eq "ADMIN") and (StaffTable.status eq "ACTIVE") }.count()
    }

    @Test
    fun createdWorderSignsInImmediatelyAndIsListedWithoutPasswordMaterial() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        val created = client.create(
            boss,
            CreateStaffRequest("Dilnoza", "fourteen chars", Role.WORDER, listOf("uz-latn", "uz-cyrl"), " Dilnoza R. ", 555L),
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<StaffDto>()
        assertEquals("dilnoza", dto.username)
        assertEquals("Dilnoza R.", dto.displayName)
        assertEquals(StaffStatus.ACTIVE, dto.status)
        assertEquals(listOf("uz-cyrl", "uz-latn"), dto.languages)
        assertEquals(555L, dto.telegramUserId)

        val me = client.login("dilnoza", "fourteen chars")
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(Role.WORDER, me.body<MeDto>().role)
        assertEquals(listOf("uz-cyrl", "uz-latn"), me.body<MeDto>().languages)

        val list = client.get(AdminRoutes.STAFF) { withSession(boss) }
        assertEquals(listOf("boss", "dilnoza"), list.body<List<StaffDto>>().map { it.username })
        val raw = list.bodyAsText().lowercase()
        assertFalse("password" in raw || "hash" in raw || "argon" in raw, raw)
        assertNotNull(list.body<List<StaffDto>>().first { it.username == "dilnoza" }.lastLoginAt)

        assertEquals(dto.id, client.get(AdminRoutes.staff(dto.id)) { withSession(boss) }.body<StaffDto>().id)
        assertEquals(HttpStatusCode.NotFound, client.get(AdminRoutes.staff("missing")) { withSession(boss) }.status)
    }

    @Test
    fun createValidationNamesTheField() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("dilnoza", telegramUserId = 777L)
        val client = adminClient()
        val boss = client.signIn("boss")

        suspend fun refused(request: CreateStaffRequest, status: HttpStatusCode, message: String) {
            val response = client.create(boss, request)
            assertEquals(status, response.status, message)
            assertEquals(message, response.error().message)
        }
        val conflict = HttpStatusCode.Conflict
        val invalid = HttpStatusCode.UnprocessableEntity
        refused(CreateStaffRequest("Dilnoza", PASSWORD, Role.WORDER, listOf("ru")), conflict, "username: taken")
        refused(CreateStaffRequest("ab", PASSWORD, Role.WORDER, listOf("ru")), invalid, "username: invalid")
        refused(CreateStaffRequest("with space", PASSWORD, Role.WORDER, listOf("ru")), invalid, "username: invalid")
        refused(CreateStaffRequest("olim", "x".repeat(11), Role.WORDER, listOf("ru")), invalid, "password: length")
        refused(CreateStaffRequest("olim", "x".repeat(129), Role.WORDER, listOf("ru")), invalid, "password: length")
        refused(CreateStaffRequest("olim", PASSWORD, Role.WORDER, emptyList()), invalid, "languages: required")
        refused(CreateStaffRequest("olim", PASSWORD, Role.WORDER, listOf("ru", "de")), invalid, "languages: unknown")
        refused(CreateStaffRequest("olim", PASSWORD, Role.WORDER, listOf("ru"), "x".repeat(65)), invalid, "displayName: too_long")
        refused(CreateStaffRequest("olim", PASSWORD, Role.WORDER, listOf("ru"), telegramUserId = 777L), conflict, "telegramUserId: taken")

        assertEquals(2L, transaction(DatabaseFactory.init()) { StaffTable.selectAll().count() }, "nothing was created")
        // An ADMIN needs no languages.
        assertEquals(HttpStatusCode.Created, client.create(boss, CreateStaffRequest("deputy", PASSWORD, Role.ADMIN)).status)
    }

    @Test
    fun editChangesFieldsAndExplicitNullClearsTheTelegramId() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val id = insertStaff("aziz", Role.WORDER, languages = listOf("ru"), telegramUserId = 42L)
        insertStaff("olim", telegramUserId = 43L)
        val client = adminClient()
        val boss = client.signIn("boss")

        val languages = client.update(boss, id, UpdateStaffRequest(languages = Patch.Set(listOf("ru", "kk")), displayName = Patch.Set("Aziz")))
        assertEquals(HttpStatusCode.OK, languages.status)
        assertEquals(listOf("kk", "ru"), languages.body<StaffDto>().languages)
        assertEquals("Aziz", languages.body<StaffDto>().displayName)
        assertEquals(42L, languages.body<StaffDto>().telegramUserId, "absent fields stay unchanged")

        assertEquals(HttpStatusCode.Conflict, client.update(boss, id, UpdateStaffRequest(telegramUserId = Patch.Set(43L))).status)

        val cleared = client.update(boss, id, UpdateStaffRequest(telegramUserId = Patch.Set(null)))
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertNull(cleared.body<StaffDto>().telegramUserId)
        assertNull(staffRow(id)[StaffTable.telegramUserId])
        assertEquals("Aziz", cleared.body<StaffDto>().displayName)

        val noLanguages = client.update(boss, id, UpdateStaffRequest(languages = Patch.Set(emptyList())))
        assertEquals(HttpStatusCode.UnprocessableEntity, noLanguages.status)
        assertEquals("languages: required", noLanguages.error().message)

        // Promote: the languages are kept for a later demotion.
        val promoted = client.update(boss, id, UpdateStaffRequest(role = Patch.Set(Role.ADMIN)))
        assertEquals(Role.ADMIN, promoted.body<StaffDto>().role)
        assertEquals(listOf("kk", "ru"), promoted.body<StaffDto>().languages)
        val aziz = client.signIn("aziz")
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.STAFF) { withSession(aziz) }.status)
    }

    @Test
    fun passwordResetEndsSessionsAndClearsTheLock() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val id = insertStaff("aziz")
        val client = adminClient()
        val boss = client.signIn("boss")
        val first = client.signIn("aziz")
        val second = client.signIn("aziz")
        transaction(DatabaseFactory.init()) {
            StaffTable.update({ StaffTable.id eq id }) { it[lockedUntil] = Instant.now().plusSeconds(900) }
        }

        val tooShort = client.post(AdminRoutes.staffPassword(id)) { withSession(boss); jsonBody(ResetPasswordRequest("short")) }
        assertEquals(HttpStatusCode.UnprocessableEntity, tooShort.status)
        assertEquals(HttpStatusCode.Locked, client.login("aziz").status)

        val reset = client.post(AdminRoutes.staffPassword(id)) { withSession(boss); jsonBody(ResetPasswordRequest("the new password")) }
        assertEquals(HttpStatusCode.NoContent, reset.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(first) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(second) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("aziz", PASSWORD).status)
        assertEquals(HttpStatusCode.OK, client.login("aziz", "the new password").status)
    }

    @Test
    fun disableEndsSessionsAndEnableRestoresSignIn() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val id = insertStaff("aziz")
        val client = adminClient()
        val boss = client.signIn("boss")
        val aziz = client.signIn("aziz")

        val disabled = client.post(AdminRoutes.staffDisable(id)) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, disabled.status)
        assertEquals(StaffStatus.DISABLED, disabled.body<StaffDto>().status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(aziz) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("aziz").status)

        val enabled = client.post(AdminRoutes.staffEnable(id)) { withSession(boss) }
        assertEquals(StaffStatus.ACTIVE, enabled.body<StaffDto>().status)
        assertEquals(HttpStatusCode.OK, client.login("aziz").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(aziz) }.status, "old sessions stay ended")
    }

    @Test
    fun thereIsNoDeleteRoute() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val id = insertStaff("aziz")
        val client = adminClient()
        val boss = client.signIn("boss")

        val response = client.delete(AdminRoutes.staff(id)) { withSession(boss) }
        assertTrue(response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed, response.status.toString())
        assertEquals(StaffStatus.ACTIVE.name, staffRow(id)[StaffTable.status])
    }

    @Test
    fun theOnlyAdminCannotDisableOrDemoteThemselves() = testApplication {
        val id = insertStaff("boss", Role.ADMIN)
        insertStaff("olim", Role.ADMIN, status = StaffStatus.DISABLED)
        val client = adminClient()
        val boss = client.signIn("boss")

        val disable = client.post(AdminRoutes.staffDisable(id)) { withSession(boss) }
        assertEquals(HttpStatusCode.Conflict, disable.status)
        assertEquals("status: last_admin", disable.error().message)

        val demote = client.update(boss, id, UpdateStaffRequest(role = Patch.Set(Role.WORDER), languages = Patch.Set(listOf("ru"))))
        assertEquals(HttpStatusCode.Conflict, demote.status)
        assertEquals("role: last_admin", demote.error().message)

        val row = staffRow(id)
        assertEquals("ADMIN", row[StaffTable.role])
        assertEquals("ACTIVE", row[StaffTable.status])
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.STAFF) { withSession(boss) }.status)
    }

    @Test
    fun oneOfTwoAdminsCanBeDisabled() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val deputyId = insertStaff("deputy", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        assertEquals(HttpStatusCode.OK, client.post(AdminRoutes.staffDisable(deputyId)) { withSession(boss) }.status)
        assertEquals("DISABLED", staffRow(deputyId)[StaffTable.status])
        assertEquals(1, activeAdminCount())
    }

    @Test
    fun concurrentDemotionsOfTheLastTwoAdminsLeaveExactlyOne() = testApplication {
        val firstId = insertStaff("first", Role.ADMIN)
        val secondId = insertStaff("second", Role.ADMIN)
        val client = adminClient()
        val first = client.signIn("first")
        val second = client.signIn("second")

        repeat(5) { round ->
            // Each demotes the other at the same time; the row locks let exactly one through.
            val statuses = coroutineScope {
                listOf(
                    async { client.update(first, secondId, UpdateStaffRequest(role = Patch.Set(Role.WORDER), languages = Patch.Set(listOf("ru")))).status },
                    async { client.update(second, firstId, UpdateStaffRequest(role = Patch.Set(Role.WORDER), languages = Patch.Set(listOf("ru")))).status },
                ).awaitAll()
            }
            assertEquals(1, activeAdminCount(), "round $round: $statuses")
            assertEquals(1, statuses.count { it == HttpStatusCode.OK }, "round $round: $statuses")

            // Restore both ADMINs for the next round.
            transaction(DatabaseFactory.init()) { StaffTable.update { it[role] = "ADMIN" } }
        }
    }

    @Test
    fun overlappingGuardTransactionsNeverRemoveBothAdmins() = runBlocking {
        // Straight at the service, so both demotions are inside their transactions at the same time (over HTTP the
        // second caller may already be refused by its permission check).
        val firstId = insertStaff("first", Role.ADMIN)
        val secondId = insertStaff("second", Role.ADMIN)
        val service = adminBackend().staff
        fun principal(id: String, name: String) = StaffPrincipal(id, name, null, Role.ADMIN, emptySet(), "session-$name")
        val demote = UpdateStaffRequest(role = Patch.Set(Role.WORDER), languages = Patch.Set(listOf("ru")))

        repeat(10) { round ->
            val outcomes = withContext(Dispatchers.IO) {
                listOf(
                    async { runCatching { service.update(principal(firstId, "first"), secondId, demote) } },
                    async { runCatching { service.disable(principal(secondId, "second"), firstId) } },
                ).awaitAll()
            }
            assertEquals(1, outcomes.count { it.isSuccess }, "round $round: $outcomes")
            val refusal = outcomes.single { it.isFailure }.exceptionOrNull()
            assertEquals(HttpStatusCode.Conflict, (refusal as AdminApiException).status, "round $round")
            assertEquals(1, activeAdminCount(), "round $round")

            transaction(DatabaseFactory.init()) {
                StaffTable.update {
                    it[role] = "ADMIN"
                    it[status] = "ACTIVE"
                }
            }
        }
    }
}
