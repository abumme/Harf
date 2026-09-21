package uz.abumme.harfgame.backend.admin

import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.ResetPasswordRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffAuditTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private fun details(row: ResultRow): JsonObject? =
        row[StaffAuditLogTable.details]?.let { Json.parseToJsonElement(it).jsonObject }

    /** Every stored value of an entry, as one string. */
    private fun text(row: ResultRow): String = StaffAuditLogTable.columns.joinToString(" ") { row[it].toString() }

    @Test
    fun everySignInAndStaffActionIsRecordedWithoutSecrets() = testApplication {
        val admin = adminBackend()
        runBlocking { admin.bootstrap.run("boss", "bootstrap password 1") }
        val client = adminClient(admin)
        val boss = client.signIn("boss", "bootstrap password 1")
        val bossId = auditRows().first()[StaffAuditLogTable.targetId]!!

        // Staff management.
        val created = client.post(AdminRoutes.STAFF) {
            withSession(boss)
            jsonBody(CreateStaffRequest("dilnoza", "first password!", Role.WORDER, listOf("ru", "kk")))
        }.body<StaffDto>()
        client.patch(AdminRoutes.staff(created.id)) { withSession(boss); jsonBody(UpdateStaffRequest(languages = Patch.Set(listOf("ru", "kk", "en")))) }
        client.post(AdminRoutes.staffPassword(created.id)) { withSession(boss); jsonBody(ResetPasswordRequest("second password!")) }
        client.post(AdminRoutes.staffDisable(created.id)) { withSession(boss) }
        client.post(AdminRoutes.staffEnable(created.id)) { withSession(boss) }

        // Sign-in outcomes of Dilnoza.
        repeat(4) { client.login("dilnoza", "wrong password $it") }
        client.login("dilnoza", "second password!") // resets the count
        repeat(5) { client.login("dilnoza", "wrong password again $it") } // the 5th locks
        client.login("ghost", "whatever password")

        // Own password change and sign-out of the ADMIN.
        client.post(AdminRoutes.AUTH_PASSWORD) {
            withSession(boss)
            jsonBody(ChangePasswordRequest("bootstrap password 1", "changed password 1"))
        }
        client.post(AdminRoutes.AUTH_LOGOUT) { withSession(boss) }

        val rows = auditRows()
        val actions = rows.map { it[StaffAuditLogTable.action] }
        // Every staff and sign-in action (word and suggestion actions have their own tests).
        for (expected in AuditActions.ALL.filter { it.startsWith("AUTH_") || it.startsWith("STAFF_") }) {
            assertTrue(expected in actions, "missing $expected in $actions")
        }

        // Creation: the ADMIN as actor, the new account, its role and languages.
        val creation = rows.single { it[StaffAuditLogTable.action] == AuditActions.STAFF_CREATED }
        assertEquals("STAFF", creation[StaffAuditLogTable.actorKind])
        assertEquals(bossId, creation[StaffAuditLogTable.actorStaffId])
        assertEquals(created.id, creation[StaffAuditLogTable.targetId])
        val creationDetails = details(creation)!!
        assertEquals("WORDER", creationDetails.getValue("role").jsonObject.getValue("to").jsonPrimitive.content)
        assertEquals(listOf("kk", "ru"), creationDetails.getValue("languages").jsonObject.getValue("to").jsonArray.map { it.jsonPrimitive.content })

        // Language change: before and after.
        val update = details(rows.single { it[StaffAuditLogTable.action] == AuditActions.STAFF_UPDATED })!!
        val languages = update.getValue("languages").jsonObject
        assertEquals(listOf("kk", "ru"), languages.getValue("from").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("en", "kk", "ru"), languages.getValue("to").jsonArray.map { it.jsonPrimitive.content })

        // A failed sign-in of an existing account names it; the lock is recorded.
        val failures = rows.filter { it[StaffAuditLogTable.action] == AuditActions.AUTH_LOGIN_FAILED }
        assertEquals(10, failures.size)
        assertEquals(9, failures.count { it[StaffAuditLogTable.targetId] == created.id })
        assertEquals(created.id, rows.single { it[StaffAuditLogTable.action] == AuditActions.AUTH_ACCOUNT_LOCKED }[StaffAuditLogTable.targetId])

        // The unknown username is not stored anywhere.
        val unknown = failures.single { it[StaffAuditLogTable.targetId] == null }
        assertNull(unknown[StaffAuditLogTable.targetType])
        assertFalse(rows.any { row -> "ghost" in text(row) })

        // No entry holds a password, a hash or a token.
        val secrets = listOf("first password!", "second password!", "bootstrap password 1", "changed password 1", "wrong password", "argon2", boss.token, boss.xsrf)
        for (row in rows) {
            val text = text(row)
            for (secret in secrets) assertFalse(secret in text, "${row[StaffAuditLogTable.action]} leaks '$secret': $text")
        }
        assertEquals(1, rows.count { it[StaffAuditLogTable.action] == AuditActions.STAFF_PASSWORD_RESET })
        assertNull(rows.single { it[StaffAuditLogTable.action] == AuditActions.STAFF_PASSWORD_RESET }[StaffAuditLogTable.details])
    }

    @Test
    fun refusedActionLeavesNoSuccessEntry() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        val refused = client.post(AdminRoutes.staffDisable(bossId)) { withSession(boss) }
        assertEquals(HttpStatusCode.Conflict, refused.status)
        val duplicate = client.post(AdminRoutes.STAFF) { withSession(boss); jsonBody(CreateStaffRequest("boss", PASSWORD, Role.ADMIN)) }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val actions = auditRows().map { it[StaffAuditLogTable.action] }
        assertFalse(AuditActions.STAFF_DISABLED in actions)
        assertFalse(AuditActions.STAFF_CREATED in actions)
    }

    @Test
    fun redactionDropsSecretKeysAtAnyDepth() {
        val redacted = AuditLog.redact(
            JsonObject(
                mapOf(
                    "role" to JsonPrimitive("ADMIN"),
                    "newPassword" to JsonPrimitive("hunter2hunter2"),
                    "passwordHash" to JsonPrimitive("\$argon2id\$..."),
                    "nested" to JsonObject(mapOf("sessionToken" to JsonPrimitive("abc"), "ok" to JsonPrimitive(1))),
                    "list" to JsonArray(listOf(JsonObject(mapOf("xsrfToken" to JsonPrimitive("t"), "lang" to JsonPrimitive("ru"))))),
                ),
            ),
        )
        assertEquals(setOf("role", "nested", "list"), redacted.keys)
        assertEquals(setOf("ok"), redacted.getValue("nested").jsonObject.keys)
        assertEquals(setOf("lang"), redacted.getValue("list").jsonArray.single().jsonObject.keys)
    }
}
