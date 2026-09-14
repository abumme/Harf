package uz.abumme.harfgame.backend.dictionary

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every lookup here runs against a fake fetcher; saved bodies are real en.wiktionary responses. */
class WiktionaryLookupTest {

    private fun fixture(name: String) = javaClass.getResource("/wiktionary/$name.json")!!.readText()

    @Test
    fun realLemmaPageSharedWithOtherLanguagesIsAutoAccepted() = runBlocking {
        val lookup = WiktionaryLookup(fetch = { fixture("ru-kniga") })
        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), lookup.lookup("ru", "книга"))
    }

    @Test
    fun realMissingPageNeedsReview() = runBlocking {
        val lookup = WiktionaryLookup(fetch = { fixture("en-missing") })
        assertEquals(LookupResult.Review(ReviewReason.NOT_FOUND), lookup.lookup("en", "zqxta"))
    }

    @Test
    fun realProperNounPageIsFlagged() = runBlocking {
        val lookup = WiktionaryLookup(fetch = { fixture("ru-pavel") })
        assertEquals(LookupResult.Review(ReviewReason.PROPER_NOUN), lookup.lookup("ru", "Павел"))
    }

    @Test
    fun requestAsksForTheExactTitleWithoutFollowingRedirects() = runBlocking {
        val urls = mutableListOf<String>()
        val result = WiktionaryLookup(fetch = { urls += it; fixture("uz-ordak") }).lookup("uz-latn", "oʻrdak")

        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), result)
        val url = urls.single()
        assertTrue(url.startsWith("https://en.wiktionary.org/w/api.php?"), url)
        assertTrue("titles=o%CA%BBrdak" in url, url) // U+02BB tutuq, percent-encoded
        assertTrue("prop=categories" in url, url)
        assertFalse("redirects" in url, url)
    }

    @Test
    fun categoriesOnLaterContinuationPagesStillCount() = runBlocking {
        val pages = ArrayDeque(
            listOf(
                """{"continue":{"clcontinue":"123|English_proper_nouns","continue":"||"},"query":{"pages":[{"pageid":123,"ns":0,"title":"bobby","categories":[{"ns":14,"title":"Category:English lemmas"}]}]}}""",
                """{"batchcomplete":true,"query":{"pages":[{"pageid":123,"ns":0,"title":"bobby","categories":[{"ns":14,"title":"Category:English proper nouns"}]}]}}""",
            )
        )
        val urls = mutableListOf<String>()
        val result = WiktionaryLookup(fetch = { urls += it; pages.removeFirst() }).lookup("en", "bobby")

        assertEquals(LookupResult.Review(ReviewReason.PROPER_NOUN), result)
        assertEquals(2, urls.size)
        assertTrue("clcontinue=123%7CEnglish_proper_nouns" in urls[1], urls[1])
    }

    @Test
    fun endlessContinuationIsUnavailable() = runBlocking {
        val page = """{"continue":{"clcontinue":"1|x","continue":"||"},"query":{"pages":[{"pageid":1,"ns":0,"title":"loop","categories":[{"ns":14,"title":"Category:English lemmas"}]}]}}"""
        var calls = 0
        val result = WiktionaryLookup(fetch = { calls++; page }).lookup("en", "loop")
        assertEquals(LookupResult.Unavailable, result)
        assertTrue(calls in 2..10, "calls=$calls")
    }

    @Test
    fun failedRequestIsUnavailable() = runBlocking {
        assertEquals(LookupResult.Unavailable, WiktionaryLookup(fetch = { null }).lookup("en", "crane"))
    }

    @Test
    fun thrownRequestErrorIsUnavailable() = runBlocking {
        val lookup = WiktionaryLookup(fetch = { throw IOException("request timed out") })
        assertEquals(LookupResult.Unavailable, lookup.lookup("en", "crane"))
    }

    @Test
    fun unreadableBodyIsUnavailable() = runBlocking {
        for (body in listOf("<html>Wikimedia error</html>", """{"error":{"code":"ratelimited","info":"slow down"}}""")) {
            assertEquals(LookupResult.Unavailable, WiktionaryLookup(fetch = { body }).lookup("en", "crane"), body)
        }
    }

    @Test
    fun disabledLookupNeedsReviewWithoutARequest() = runBlocking {
        var calls = 0
        val result = WiktionaryLookup(enabled = false, fetch = { calls++; fixture("ru-kniga") }).lookup("ru", "книга")
        assertEquals(LookupResult.Review(ReviewReason.DISABLED), result)
        assertEquals(0, calls)
    }

    @Test
    fun unmappedLanguageNeedsReviewWithoutARequest() = runBlocking {
        var calls = 0
        val result = WiktionaryLookup(fetch = { calls++; fixture("ru-kniga") }).lookup("de", "buch")
        assertEquals(LookupResult.Review(ReviewReason.NOT_FOUND), result)
        assertEquals(0, calls)
    }
}
