package uz.abumme.harfgame.data.auth

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class SessionData(
    val userId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val isLinked: Boolean = false,
    /** User-confirmed display name for the linked account; null when anonymous / unnamed. */
    val displayName: String? = null,
)

class SessionStore(private val ksafe: KSafe) {
    private val _session = MutableStateFlow<SessionData?>(null)
    val sessionFlow: Flow<SessionData?> = _session.asStateFlow()

    suspend fun get(): SessionData {
        val data = ksafe.get(KEY, SessionData())
        _session.value = data
        return data
    }

    suspend fun saveSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
        isLinked: Boolean? = null,
        displayName: String? = null,
    ) {
        val current = get()
        val updated = SessionData(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            isLinked = isLinked ?: current.isLinked,
            displayName = displayName ?: current.displayName,
        )
        ksafe.put(KEY, updated)
        _session.value = updated
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val current = get()
        val updated = current.copy(accessToken = accessToken, refreshToken = refreshToken)
        ksafe.put(KEY, updated)
        _session.value = updated
    }

    suspend fun setLinked(isLinked: Boolean) {
        val current = get()
        val updated = current.copy(isLinked = isLinked)
        ksafe.put(KEY, updated)
        _session.value = updated
    }

    suspend fun clear() {
        val cleared = SessionData()
        ksafe.put(KEY, cleared)
        _session.value = cleared
    }

    companion object {
        private const val KEY = "auth.session"
    }
}
