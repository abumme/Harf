package uz.abumme.harfgame.data.wordpack

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry

/**
 * Offline-first word-pack sync. Fetches per language sending `If-None-Match` with the cached version;
 * a `200` with an adoptable pack is validated and cached, a `304` or any error is a no-op. Never
 * throws to the caller and never blocks gameplay — the repository reads whatever is currently cached
 * or bundled.
 */
class WordPackSyncManager(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val cache: WordPackCache,
    private val repository: WordPackRepository,
    private val registry: LanguageRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    /** Fire-and-forget sync of every language — call on launch/foreground. */
    fun syncAll() {
        scope.launch {
            for (lang in registry.ids) syncOne(lang)
        }
    }

    suspend fun syncOne(lang: String) {
        try {
            val currentVersion = cache.get(lang)?.version
            val response = httpClient.get("$baseUrl${ApiRoutes.wordpack(lang)}") {
                if (currentVersion != null) header(HttpHeaders.IfNoneMatch, currentVersion)
            }
            if (response.status == HttpStatusCode.OK) {
                val dto = response.body<WordPackDto>()
                if (repository.isAdoptable(dto)) {
                    cache.put(dto)
                    repository.invalidate(lang)
                }
            }
            // 304 / other statuses: keep the current cache
        } catch (_: Exception) {
            // Offline or transport error: never surfaced, keep playing from cache/bundle.
        }
    }
}
