package uz.abumme.harfgame.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.audit.ActorKind
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.ResetPasswordRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminSerializationTest {
    // The same settings as the backend's ContentNegotiation.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun authDtosRoundTrip() {
        val login = LoginRequest("aziz", "correct horse battery")
        assertEquals(login, json.decodeFromString(json.encodeToString(login)))

        val me = MeDto(
            id = "s-1",
            username = "aziz",
            displayName = "Aziz",
            role = Role.WORDER,
            languages = listOf("uz-latn", "uz-cyrl"),
            permissions = setOf(Permission.WORDS_READ, Permission.AUDIT_READ_OWN, Permission.ACCOUNT_SELF),
        )
        assertEquals(me, json.decodeFromString(json.encodeToString(me)))

        val change = ChangePasswordRequest("old password!", "new password!!")
        assertEquals(change, json.decodeFromString(json.encodeToString(change)))
    }

    @Test
    fun staffDtosRoundTrip() {
        val staff = StaffDto(
            id = "s-2",
            username = "dilnoza",
            role = Role.WORDER,
            status = StaffStatus.DISABLED,
            languages = listOf("ru"),
            telegramUserId = 123456789L,
            lockedUntil = 1_800_000_000_000,
            lastLoginAt = null,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_000_001,
        )
        assertEquals(staff, json.decodeFromString(json.encodeToString(staff)))

        val create = CreateStaffRequest("dilnoza", "fourteen chars", Role.WORDER, listOf("uz-latn"), null, 42L)
        assertEquals(create, json.decodeFromString(json.encodeToString(create)))

        val reset = ResetPasswordRequest("another password")
        assertEquals(reset, json.decodeFromString(json.encodeToString(reset)))
    }

    @Test
    fun updateLeavesAbsentFieldsOutAndKeepsExplicitNull() {
        val update = UpdateStaffRequest(telegramUserId = Patch.Set(null), languages = Patch.Set(listOf("ru", "kk")))
        val encoded = json.parseToJsonElement(json.encodeToString(update)).jsonObject

        assertEquals(setOf("telegramUserId", "languages"), encoded.keys)
        assertEquals(JsonNull, encoded["telegramUserId"])
        assertEquals(update, json.decodeFromString(encoded.toString()))
    }

    @Test
    fun updateDecodesAbsentAsUnchangedAndNullAsClear() {
        val absent = json.decodeFromString<UpdateStaffRequest>("""{}""")
        assertEquals(UpdateStaffRequest(), absent)
        assertEquals(Patch.Unchanged, absent.telegramUserId)
        assertEquals(Patch.Unchanged, absent.displayName)

        val cleared = json.decodeFromString<UpdateStaffRequest>("""{"telegramUserId":null,"displayName":null}""")
        assertEquals(Patch.Set(null), cleared.telegramUserId)
        assertEquals(Patch.Set(null), cleared.displayName)
        assertEquals(Patch.Unchanged, cleared.role)

        val set = json.decodeFromString<UpdateStaffRequest>("""{"role":"ADMIN","telegramUserId":77}""")
        assertEquals(Patch.Set(Role.ADMIN), set.role)
        assertEquals(Patch.Set(77L), set.telegramUserId)
    }

    @Test
    fun updateRefusesNullForNonNullableField() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<UpdateStaffRequest>("""{"role":null}""")
        }
    }

    @Test
    fun auditPageRoundTrip() {
        val details: JsonObject = buildJsonObject {
            put("languages", buildJsonObject {
                put("from", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("ru"))))
                put("to", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("ru"), JsonPrimitive("kk"))))
            })
        }
        val page = PageDto(
            items = listOf(
                AuditEntryDto(
                    id = "a-1",
                    at = 1_700_000_000_000,
                    actorKind = ActorKind.STAFF,
                    actorStaffId = "s-1",
                    actorUsername = "aziz",
                    action = "STAFF_UPDATED",
                    targetType = "STAFF",
                    targetId = "s-2",
                    targetLabel = "dilnoza",
                    details = details,
                ),
                AuditEntryDto(id = "a-2", at = 1, actorKind = ActorKind.SYSTEM, action = "STAFF_BOOTSTRAPPED"),
            ),
            page = 0,
            size = 50,
            total = 2,
        )
        assertEquals(page, json.decodeFromString<PageDto<AuditEntryDto>>(json.encodeToString(page)))
    }

    @Test
    fun fieldErrorMessageRoundTrip() {
        val error = FieldError("username", "taken")
        assertEquals("username: taken", error.toMessage())
        assertEquals(error, FieldError.parse(error.toMessage()))
        assertEquals(FieldError("currentPassword", "wrong"), FieldError.parse("currentPassword: wrong"))
        assertNull(FieldError.parse("Invalid username or password"))
        assertNull(FieldError.parse(null))
    }

    @Test
    fun staffRules() {
        assertEquals("aziz", StaffRules.normalizeUsername("  Aziz "))
        assertTrue(StaffRules.isValidUsername("dilnoza.r_1-x"))
        assertFalse(StaffRules.isValidUsername("ab"))
        assertFalse(StaffRules.isValidUsername("a".repeat(33)))
        assertFalse(StaffRules.isValidUsername("Aziz"))
        assertFalse(StaffRules.isValidUsername("aziz bek"))
        assertFalse(StaffRules.isValidPassword("x".repeat(11)))
        assertTrue(StaffRules.isValidPassword("x".repeat(12)))
        assertTrue(StaffRules.isValidPassword("x".repeat(128)))
        assertFalse(StaffRules.isValidPassword("x".repeat(129)))
    }

    @Test
    fun routesHangOffTheApiPrefix() {
        assertEquals("/api/v1/admin/auth/login", AdminRoutes.AUTH_LOGIN)
        assertEquals("/api/v1/admin/staff/abc/disable", AdminRoutes.staffDisable("abc"))
    }
}
