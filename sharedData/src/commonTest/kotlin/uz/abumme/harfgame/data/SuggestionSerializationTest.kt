package uz.abumme.harfgame.data

import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSuggestWordRequestRoundTrip() {
        val req = SuggestWordRequest(lang = "uz-latn", word = "salom")
        assertEquals(req, json.decodeFromString(json.encodeToString(req)))
    }

    @Test
    fun testSuggestWordResponseRoundTrip() {
        val resp = SuggestWordResponse(status = SuggestionStatus.PENDING.name)
        val decoded = json.decodeFromString<SuggestWordResponse>(json.encodeToString(resp))
        assertEquals(resp, decoded)
        assertEquals("PENDING", decoded.status)
    }
}
