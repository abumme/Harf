package uz.abumme.harfgame.admin

import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.forms.PasswordChangeState
import uz.abumme.harfgame.admin.forms.StaffFormState
import uz.abumme.harfgame.admin.forms.formFailure
import uz.abumme.harfgame.admin.forms.loginMessage
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginMessagesTest {
    @Test
    fun refusalsMapToTheirMessages() {
        assertEquals(Strings.Login.INVALID, loginMessage(ApiResult.Error("unauthorized", "Invalid username or password")))
        assertEquals(Strings.Login.LOCKED, loginMessage(ApiResult.Error("locked", "Too many failed sign-in attempts")))
        assertEquals(Strings.Login.RATE_LIMITED, loginMessage(ApiResult.Error("rate_limited", "Too many")))
        assertEquals(Strings.Common.NETWORK_ERROR, loginMessage(ApiResult.Error("network_error", "No response")))
        assertEquals(Strings.Common.SERVER_ERROR, loginMessage(ApiResult.Error("server_error", "502")))
    }
}

class StaffFormTest {
    private val original = StaffDto(
        id = "s-2",
        username = "dilnoza",
        displayName = "Dilnoza",
        role = Role.WORDER,
        status = StaffStatus.ACTIVE,
        languages = listOf("ru"),
        telegramUserId = 42L,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun createValidationMirrorsTheServerRules() {
        val empty = StaffFormState().validate(creating = true)
        assertEquals(mapOf("username" to "required", "password" to "required", "languages" to "required"), empty)

        val bad = StaffFormState(username = "ab", password = "short", displayName = "x".repeat(65), telegramUserId = "12a")
            .validate(creating = true)
        assertEquals(
            mapOf(
                "username" to "invalid",
                "password" to "length",
                "displayName" to "too_long",
                "languages" to "required",
                "telegramUserId" to "invalid",
            ),
            bad,
        )

        val good = StaffFormState(username = " Dilnoza ", password = "fourteen chars", languages = setOf("uz-latn"), telegramUserId = "123")
        assertEquals(emptyMap(), good.validate(creating = true))
        assertEquals("dilnoza", good.toCreateRequest().username)
        assertEquals(123L, good.toCreateRequest().telegramUserId)
        assertNull(good.toCreateRequest().displayName)

        // An ADMIN needs no languages.
        assertEquals(emptyMap(), good.copy(role = Role.ADMIN, languages = emptySet()).validate(creating = true))
    }

    @Test
    fun editSendsOnlyChangesAndExplicitNullForClearedFields() {
        val unchanged = StaffFormState.from(original)
        assertEquals(emptyMap(), unchanged.validate(creating = false), "username and password are not checked on edit")
        val noChanges = unchanged.toUpdateRequest(original)
        assertEquals(Patch.Unchanged, noChanges.displayName)
        assertEquals(Patch.Unchanged, noChanges.role)
        assertEquals(Patch.Unchanged, noChanges.languages)
        assertEquals(Patch.Unchanged, noChanges.telegramUserId)

        val changed = unchanged.copy(displayName = " ", telegramUserId = "", languages = setOf("kk", "ru")).toUpdateRequest(original)
        assertEquals(Patch.Set(null), changed.displayName)
        assertEquals(Patch.Set(null), changed.telegramUserId)
        assertEquals(Patch.Set(listOf("kk", "ru")), changed.languages)

        assertEquals(mapOf("languages" to "required"), unchanged.copy(languages = emptySet()).validate(creating = false))
    }

    @Test
    fun serverFieldErrorsGoNextToTheirField() {
        val taken = formFailure(ApiResult.Error("conflict", "username: taken"), StaffFormState.CREATE_FIELDS)
        assertEquals(mapOf("username" to "taken"), taken.fieldErrors)
        assertNull(taken.message)

        // The edit form has no username field: the same error becomes a message for the whole form.
        val elsewhere = formFailure(ApiResult.Error("conflict", "username: taken"), StaffFormState.EDIT_FIELDS)
        assertEquals(emptyMap(), elsewhere.fieldErrors)
        assertEquals(Strings.fieldReason("username", "taken"), elsewhere.message)

        val lastAdmin = formFailure(ApiResult.Error("conflict", "role: last_admin"), StaffFormState.EDIT_FIELDS)
        assertEquals(mapOf("role" to "last_admin"), lastAdmin.fieldErrors)

        val network = formFailure(ApiResult.Error("network_error", "No response"), StaffFormState.EDIT_FIELDS)
        assertEquals(Strings.Common.NETWORK_ERROR, network.message)
    }
}

class PasswordFormTest {
    @Test
    fun mismatchBlocksSubmit() {
        val mismatch = PasswordChangeState("old password!", "a new password", "a new passw0rd")
        assertFalse(mismatch.canSubmit)
        assertEquals(mapOf("repeatPassword" to FormReasons.MISMATCH), mismatch.validate())
        assertEquals("Пароли не совпадают.", Strings.fieldReason("repeatPassword", FormReasons.MISMATCH))

        assertTrue(PasswordChangeState("old password!", "a new password", "a new password").canSubmit)
        assertEquals(mapOf("newPassword" to "length"), PasswordChangeState("old", "short", "short").validate())
        assertEquals(mapOf("currentPassword" to "required", "newPassword" to "required"), PasswordChangeState().validate())
    }
}

class PagingStateTest {
    @Test
    fun pageBounds() {
        val paging = PagingState(page = 0, size = 50, total = 120)
        assertEquals(3, paging.pageCount)
        assertFalse(paging.hasPrevious)
        assertTrue(paging.hasNext)
        assertEquals(1L to 50L, paging.firstItem to paging.lastItem)

        val last = paging.goTo(2)
        assertFalse(last.hasNext)
        assertEquals(101L to 120L, last.firstItem to last.lastItem)
        assertEquals(last, last.next(), "never past the last page")
        assertEquals(paging, paging.previous(), "never before the first page")
        assertEquals(2, paging.goTo(99).page)

        val empty = PagingState(total = 0)
        assertEquals(1, empty.pageCount)
        assertEquals(0L to 0L, empty.firstItem to empty.lastItem)
        assertFalse(empty.hasNext)
    }

    @Test
    fun shrinkingTotalMovesBackAndFilterChangeResetsToFirstPage() {
        val third = PagingState(page = 2, size = 50, total = 120)
        assertEquals(1, third.withTotal(60).page)
        assertEquals(0, third.resetForFilterChange().page)
        assertEquals(50, third.resetForFilterChange().size)
    }
}
