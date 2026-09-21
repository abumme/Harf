package uz.abumme.harfgame.admin

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.AuditQuery
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.session.SessionFlow
import uz.abumme.harfgame.admin.session.SessionStatus
import uz.abumme.harfgame.admin.session.SessionStore
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionFlowTest {
    private val me = MeDto("s-1", "boss", null, Role.ADMIN, listOf("en", "ru"), Permission.entries.toSet())

    @Test
    fun unauthorizedClearsTheStateBeforeGoingToLoginWithNext() = runTest {
        val store = SessionStore { ApiResult.Success(me) }
        store.load()
        assertTrue(store.hasPermission(Permission.STAFF_MANAGE))

        val navigations = mutableListOf<Triple<String, Boolean, SessionStatus>>()
        val flow = SessionFlow(
            store,
            navigate = { route, replace -> navigations += Triple(route, replace, store.status) },
            currentRoute = { "/audit?page=3" },
        )
        val api = AdminApi(
            "/harf",
            CredentialsMode.SAME_ORIGIN,
            { HttpResponse(401, """{"error":"unauthorized","message":"Not signed in"}""") },
            { "" },
            flow::onUnauthorized,
        )

        assertIs<ApiResult.Error>(api.audit(AuditQuery()))

        val (route, replace, statusWhenNavigating) = navigations.single()
        assertEquals("/login?next=%2Faudit%3Fpage%3D3", route)
        assertTrue(replace)
        assertEquals(SessionStatus.SignedOut, statusWhenNavigating, "state is cleared before navigating")
        assertNull(store.me)
        assertFalse(store.hasPermission(Permission.STAFF_MANAGE))
    }

    @Test
    fun unauthorizedOnTheLoginPageDoesNotNavigate() {
        val store = SessionStore { ApiResult.Error("unauthorized", "") }
        val navigations = mutableListOf<String>()
        SessionFlow(store, { route, _ -> navigations += route }, { "/login?next=%2Fstaff" }).onUnauthorized()
        assertEquals(emptyList(), navigations)
    }

    @Test
    fun signOutEndsTheServerSessionThenClearsAndGoesToLogin() = runTest {
        val store = SessionStore { ApiResult.Success(me) }
        store.load()
        val events = mutableListOf<String>()
        val flow = SessionFlow(store, { route, _ -> events += "navigate $route while ${store.status}" }, { "/staff" })

        flow.signOut {
            events += "logout while signed in: ${store.me != null}"
            ApiResult.Success(Unit)
        }

        assertEquals(listOf("logout while signed in: true", "navigate /login while SignedOut"), events)
    }

    @Test
    fun meIsLoadedOnceAndFailuresAreDistinguished() = runTest {
        var calls = 0
        val store = SessionStore {
            calls++
            ApiResult.Success(me)
        }
        store.load()
        store.load()
        assertEquals(1, calls)
        store.load(force = true)
        assertEquals(2, calls)

        val signedOut = SessionStore { ApiResult.Error("unauthorized", "Not signed in") }
        signedOut.load()
        assertEquals(SessionStatus.SignedOut, signedOut.status)

        val offline = SessionStore { ApiResult.Error("network_error", "No response") }
        offline.load()
        assertEquals(SessionStatus.Failed("network_error"), offline.status)
    }
}
