package uz.abumme.harfgame.admin.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.api.ApiResult

sealed interface SessionStatus {
    /** Nothing asked yet (fresh page load). */
    data object Unknown : SessionStatus

    data object Loading : SessionStatus

    data object SignedOut : SessionStatus

    data class SignedIn(val me: MeDto) : SessionStatus

    /** `me` could not be loaded for another reason (network, server error). */
    data class Failed(val code: String) : SessionStatus
}

/**
 * The signed-in staff member, loaded once from `GET /auth/me` and shared by every page. It holds nothing else, so
 * clearing it on sign-out or on a 401 leaves no staff data behind for the Back button.
 */
class SessionStore(private val fetchMe: suspend () -> ApiResult<MeDto>) {
    var status: SessionStatus by mutableStateOf(SessionStatus.Unknown)
        private set

    val me: MeDto? get() = (status as? SessionStatus.SignedIn)?.me

    fun hasPermission(permission: Permission): Boolean = me?.permissions?.contains(permission) == true

    /** Loads `me` unless it is already known (or [force]). */
    suspend fun load(force: Boolean = false) {
        if (!force && (status is SessionStatus.SignedIn || status is SessionStatus.SignedOut || status is SessionStatus.Loading)) return
        status = SessionStatus.Loading
        status = when (val result = fetchMe()) {
            is ApiResult.Success -> SessionStatus.SignedIn(result.data)
            is ApiResult.Error -> if (result.code == "unauthorized") SessionStatus.SignedOut else SessionStatus.Failed(result.code)
        }
    }

    fun signedIn(me: MeDto) {
        status = SessionStatus.SignedIn(me)
    }

    /** Forgets the member; the next protected page goes to login. */
    fun clear() {
        status = SessionStatus.SignedOut
    }
}

/**
 * What happens when the session ends: on a 401 the state is cleared first, then the panel goes to login with the
 * current page as `next`; signing out ends the server session, clears the state and goes to login.
 */
class SessionFlow(
    private val store: SessionStore,
    private val navigate: (route: String, replace: Boolean) -> Unit,
    private val currentRoute: () -> String,
) {
    fun onUnauthorized() {
        val current = currentRoute()
        store.clear()
        if (current.substringBefore('?') != Routes.LOGIN) navigate(Routes.login(next = current), true)
    }

    suspend fun signOut(logout: suspend () -> ApiResult<Unit>) {
        logout()
        store.clear()
        navigate(Routes.LOGIN, true)
    }
}
