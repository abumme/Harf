package uz.abumme.harfgame.backend.dictionary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Verifies words against English Wiktionary's categories API and classifies them with [WiktionaryClassifier].
 * The page title must equal the word — MediaWiki redirects are not requested — and anything the lookup
 * cannot read completely (failed request, error body, endless continuation) is [LookupResult.Unavailable],
 * never a guess. [fetch] returns a response body, or null when the request failed.
 */
class WiktionaryLookup(
    private val enabled: Boolean = true,
    private val fetch: suspend (url: String) -> String? = { httpGet(it) },
) : WordLookup {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lookup(lang: String, word: String): LookupResult {
        if (!enabled) return LookupResult.Review(ReviewReason.DISABLED)
        if (WiktionaryClassifier.languageName(lang) == null) return LookupResult.Review(ReviewReason.NOT_FOUND)
        val categories = try {
            fetchCategories(word)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return LookupResult.Unavailable
        return WiktionaryClassifier.classify(lang, categories)
    }

    /** Every category title of [word]'s page (empty for a missing page), or null when a response is unreadable. */
    private suspend fun fetchCategories(word: String): List<String>? {
        val categories = mutableListOf<String>()
        var continuation = emptyMap<String, String>()
        repeat(MAX_PAGES) {
            val root = json.parseToJsonElement(fetch(url(word, continuation)) ?: return null).jsonObject
            val page = root["query"]?.jsonObject?.get("pages")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            page["categories"]?.jsonArray?.forEach { category ->
                category.jsonObject["title"]?.jsonPrimitive?.contentOrNull?.let(categories::add)
            }
            continuation = root["continue"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: return categories
        }
        return null // still continuing: a truncated list could hide a blocking category
    }

    private fun url(word: String, continuation: Map<String, String>): String = buildString {
        append("$API?action=query&format=json&formatversion=2&prop=categories&cllimit=max&titles=")
        append(encode(word))
        continuation.forEach { (key, value) -> append('&').append(encode(key)).append('=').append(encode(value)) }
    }

    private companion object {
        const val API = "https://en.wiktionary.org/w/api.php"
        const val MAX_PAGES = 5
        const val USER_AGENT = "HarfBackend/1.0 (https://github.com/abumme/Harf; word suggestion verification)"
        val TIMEOUT: Duration = Duration.ofSeconds(5)

        val client: HttpClient by lazy {
            HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        }

        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

        suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() in 200..299 }
        }
    }
}
