package uz.abumme.harfgame.data.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.service.AuthService
import uz.abumme.harfgame.data.service.SuggestionService
import uz.abumme.harfgame.data.service.SyncService
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import uz.abumme.harfgame.lang.LanguageRegistry

fun ResultRecord.toDto() = ResultRecordDto(
    language = language,
    puzzleDay = puzzleDay,
    won = won,
    attempts = attempts,
    roundKind = roundKind,
    hardMode = hardMode,
)

fun ResultRecordDto.toDomain() = ResultRecord(
    language = language,
    puzzleDay = puzzleDay,
    won = won,
    attempts = attempts,
    roundKind = roundKind,
    hardMode = hardMode,
)

class SyncManager(
    private val resultLog: ResultLog,
    private val sessionStore: SessionStore,
    private val authService: AuthService,
    private val syncService: SyncService,
    private val suggestionService: SuggestionService,
    private val pendingStore: PendingUploadStore,
    private val roundStore: RoundStore,
    private val languageRegistry: LanguageRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    fun bootstrap() {
        scope.launch {
            if (ensureSession()) {
                pullStats()
                flushPending() // retry any offline result recorded on a previous run
            }
        }
    }

    /** Ensure an account/session exists, creating an anonymous one if needed. */
    private suspend fun ensureSession(): Boolean {
        val session = sessionStore.get()
        if (session.refreshToken != null) return true
        return authService.createAnonymousAccount() is ApiResult.Success
    }

    /**
     * Record that stats need uploading (durably) and try to flush now. The durable marker means an
     * upload that fails here (e.g. offline) is retried on the next launch/foreground/reconnect.
     */
    suspend fun pushStats() {
        pendingStore.markDirty()
        flushPending()
    }

    /** Retry a pending upload — call on app foreground or connectivity restore (fire-and-forget). */
    fun retryPending() {
        scope.launch {
            if (ensureSession()) flushPending()
        }
    }

    /** Suspending flush of any pending upload — for callers already inside a coroutine/tests. */
    suspend fun syncPendingUploads() = flushPending()

    private suspend fun flushPending() {
        if (!pendingStore.isDirty()) return
        val token = sessionStore.get().accessToken ?: return // stay dirty; retry later
        val dto = UserStatsDto(
            updatedAt = resultLog.getUpdatedAt(),
            records = resultLog.all().map { it.toDto() },
        )
        if (syncService.uploadStats(token, dto) is ApiResult.Success) {
            pendingStore.clear()
        }
    }

    suspend fun pullStats(replace: Boolean = false): Boolean {
        val token = sessionStore.get().accessToken ?: return false
        val result = syncService.getStats(token)
        if (result is ApiResult.Success) {
            val records = result.data.records.map { it.toDomain() }
            if (replace) resultLog.replace(records, result.data.updatedAt)
            else resultLog.merge(records, result.data.updatedAt)
            return true
        }
        return false
    }

    suspend fun linkAccount(
        provider: OAuthProvider,
        idToken: String,
        nonce: String? = null,
        displayName: String? = null,
    ): ApiResult<Unit> {
        val token = sessionStore.get().accessToken
            ?: return ApiResult.Error("UNAUTHORIZED", "No active session")
        val linkResult = authService.linkAccount(token, LinkAccountRequest(provider, idToken, nonce, displayName))
        return when (linkResult) {
            is ApiResult.Success -> {
                // Server-wins: adopt the pre-existing account's snapshot and drop any pending marker
                // so the local mix is never uploaded into it. A failed pull leaves upload suppressed
                // until a later successful pull rather than pushing local data.
                // ponytail: pending is cleared even if pull fails — safe direction; next round re-marks.
                pullStats(replace = true)
                pendingStore.clear()
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> ApiResult.Error(linkResult.code, linkResult.message)
        }
    }

    /**
     * Submit a word suggestion for review. Ensures a session first (creating an anonymous one if
     * needed), so a player can suggest even before they've linked an account.
     */
    suspend fun suggestWord(lang: String, word: String): ApiResult<Unit> {
        if (!ensureSession()) return ApiResult.Error("UNAUTHORIZED", "No active session")
        val token = sessionStore.get().accessToken
            ?: return ApiResult.Error("UNAUTHORIZED", "No active session")
        return when (val r = suggestionService.suggest(token, SuggestWordRequest(lang, word))) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> ApiResult.Error(r.code, r.message)
        }
    }

    /**
     * Sign out: revoke the account's refresh tokens on the server, clear local session/credential
     * and stats state, then re-establish a fresh anonymous session so offline-first play continues.
     */
    suspend fun logout(): ApiResult<Unit> {
        val token = sessionStore.get().accessToken
        val result = if (token != null) authService.logout(token) else ApiResult.Success(Unit)
        wipeLocalState()
        bootstrap()
        return result
    }

    /**
     * Delete the server account. Local state (session, stats, rounds, pending marker) is cleared
     * ONLY on a confirmed server success; on failure everything is preserved and a retryable error
     * is returned so the user can try again without losing data.
     */
    suspend fun deleteAccount(): ApiResult<Unit> {
        val token = sessionStore.get().accessToken
        if (token == null) {
            // No server account to delete; just reset to a fresh local state.
            wipeLocalState()
            bootstrap()
            return ApiResult.Success(Unit)
        }
        return when (val result = authService.deleteAccount(token)) {
            is ApiResult.Success -> {
                wipeLocalState()
                bootstrap()
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result // keep session + local data; surface retryable error
        }
    }

    private suspend fun wipeLocalState() {
        sessionStore.clear()
        resultLog.clear()
        pendingStore.clear()
        roundStore.clearAll(languageRegistry.ids)
    }
}
