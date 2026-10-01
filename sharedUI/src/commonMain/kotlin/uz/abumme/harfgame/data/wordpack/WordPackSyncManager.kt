package uz.abumme.harfgame.data.wordpack

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.time.Duration

/**
 * What resolving today's word needs from pack sync on a fresh install: whether a language already has a cached server
 * pack, and a bounded wait for the first sync of this app session.
 */
interface FirstPackSync {
    suspend fun hasCachedPack(lang: String): Boolean

    /** Returns once [lang]'s first sync attempt of this session has ended (any outcome) or [timeout] passed; never throws. */
    suspend fun awaitFirstSync(lang: String, timeout: Duration)
}

/**
 * Offline-first word-pack sync. Fetches per language sending `If-None-Match` with the cached version;
 * a `200` with an adoptable pack is validated and cached, a `304` or any error is a no-op. Never
 * throws to the caller and never blocks gameplay — the repository reads whatever is currently cached
 * or bundled. The end of each language's first sync attempt of the session (success, 304, error or
 * offline) is observable through [awaitFirstSync], so a fresh install can briefly wait for the
 * published calendar.
 */
class WordPackSyncManager(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val cache: WordPackCache,
    private val repository: WordPackRepository,
    private val registry: LanguageRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : FirstPackSync {
    private val firstSyncs: Map<String, CompletableDeferred<Unit>> = registry.ids.associateWith { CompletableDeferred() }

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
                // Adopt the pack the integrity check built rather than evicting it and rebuilding on the next open.
                repository.build(dto)?.let { pack ->
                    cache.put(dto) // persist first: memory never runs ahead of what a restart would load
                    repository.adopt(pack)
                }
            }
            // 304 / other statuses: keep the current cache
        } catch (_: Exception) {
            // Offline or transport error: never surfaced, keep playing from cache/bundle.
        } finally {
            firstSyncs[lang]?.complete(Unit) // only the first completion counts
        }
    }

    /**
     * Once [lang]'s first sync of the session has ended, [awaitFirstSync] returns at once whatever is cached, so this
     * skips decoding the whole cached pack just to null-check it.
     */
    override suspend fun hasCachedPack(lang: String): Boolean =
        firstSyncs[lang]?.isCompleted == true || cache.get(lang) != null

    override suspend fun awaitFirstSync(lang: String, timeout: Duration) {
        val first = firstSyncs[lang] ?: return
        withTimeoutOrNull(timeout) { first.await() }
    }
}
