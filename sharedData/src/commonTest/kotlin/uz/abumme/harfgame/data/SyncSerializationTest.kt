package uz.abumme.harfgame.data

import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.RoundKind
import uz.abumme.harfgame.data.sync.UserStatsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncSerializationTest {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testLegacyResultRecordDtoDeserializesAsNormalOfficialRound() {
        val legacyJson = """{"language":"uz","puzzleDay":19500,"won":true,"attempts":4}"""
        val decoded = json.decodeFromString<ResultRecordDto>(legacyJson)
        assertEquals("uz", decoded.language)
        assertEquals(19500L, decoded.puzzleDay)
        assertEquals(true, decoded.won)
        assertEquals(4, decoded.attempts)
        assertEquals(RoundKind.OFFICIAL, decoded.roundKind)
        assertFalse(decoded.hardMode)
    }

    @Test
    fun testUserStatsDtoSerialization() {
        val original = UserStatsDto(
            updatedAt = Instant.parse("2026-08-27T12:00:00Z"),
            records = listOf(
                ResultRecordDto(
                    language = "uz",
                    puzzleDay = 19500L,
                    won = true,
                    attempts = 4
                ),
                ResultRecordDto(
                    language = "en",
                    puzzleDay = 19501L,
                    won = false,
                    attempts = 6
                )
            )
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<UserStatsDto>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun testApiResultSerialization() {
        val success: ApiResult<String> = ApiResult.Success("test-payload")
        val serializedSuccess = json.encodeToString(success)
        val deserializedSuccess = json.decodeFromString<ApiResult<String>>(serializedSuccess)
        assertEquals(success, deserializedSuccess)

        val error: ApiResult<String> = ApiResult.Error("UNAUTHORIZED", "Invalid token")
        val serializedError = json.encodeToString(error)
        val deserializedError = json.decodeFromString<ApiResult<String>>(serializedError)
        assertEquals(error, deserializedError)

        val errorResp = ApiErrorResponse(error = "invalid_token", message = "Expired")
        val serializedResp = json.encodeToString(errorResp)
        val deserializedResp = json.decodeFromString<ApiErrorResponse>(serializedResp)
        assertEquals(errorResp, deserializedResp)
    }
}
