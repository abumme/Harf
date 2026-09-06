package uz.abumme.harfgame.data

import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.wordpack.WordPackDto
import kotlin.test.Test
import kotlin.test.assertEquals

class WordPackSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun wordPackDtoRoundTrips() {
        val dto = WordPackDto(
            lang = "en",
            version = "1",
            effectiveFrom = 20000L,
            anchorEpochDay = 20000L,
            answers = listOf("bread", "crane"),
            guesses = listOf("bread", "crane", "slate"),
            schedule = listOf("bread", "crane", "bread"),
        )
        val decoded = json.decodeFromString<WordPackDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun wordpackRouteBuildsPath() {
        assertEquals("/api/v1/wordpacks/en", ApiRoutes.wordpack("en"))
    }
}
