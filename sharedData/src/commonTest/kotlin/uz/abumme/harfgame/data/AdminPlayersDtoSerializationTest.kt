package uz.abumme.harfgame.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.EndSessionsResultDto
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerLanguageStatsDto
import uz.abumme.harfgame.data.admin.players.PlayerParams
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerSuggestionDto
import uz.abumme.harfgame.data.admin.players.PlayerSummaryDto
import uz.abumme.harfgame.data.admin.players.PlayerType
import uz.abumme.harfgame.data.admin.players.SuggestionBlockDto
import uz.abumme.harfgame.data.admin.players.SuggestionCountsDto
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminPlayersDtoSerializationTest {
    // The same settings as the backend's ContentNegotiation.
    private val json = Json { ignoreUnknownKeys = true }

    private val id = "3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"

    private val detail = PlayerDetailDto(
        id = id,
        createdAt = 1_700_000_000_000,
        displayName = "Alisher",
        providers = listOf(OAuthProvider.APPLE, OAuthProvider.GOOGLE),
        lastStatsSnapshotAt = 1_700_000_500_000,
        activeSessions = 2,
        languages = listOf(PlayerLanguageStatsDto("ru", games = 3, wins = 2, winRate = 2f / 3, currentStreak = 1, bestStreak = 2)),
        suggestionCounts = SuggestionCountsDto(pending = 1, accepted = 4, rejected = 0),
        recentSuggestions = listOf(
            PlayerSuggestionDto("s-1", "ru", "берег", SuggestionStatus.ACCEPTED, 1_700_000_100_000, 1_700_000_200_000),
            PlayerSuggestionDto("s-2", "en", "crane", SuggestionStatus.PENDING, 1_700_000_300_000),
        ),
        suggestionBlock = SuggestionBlockDto(1_700_000_400_000, StaffRefDto("st-1", "boss")),
    )

    @Test
    fun playerDtosRoundTrip() {
        assertEquals(detail, json.decodeFromString(json.encodeToString(detail)))
        val bare = PlayerDetailDto(id = id, createdAt = 1)
        assertEquals(bare, json.decodeFromString(json.encodeToString(bare)))

        val page = PlayerSearchPageDto(
            items = listOf(
                PlayerSummaryDto(id, "Vali", listOf(OAuthProvider.GOOGLE), 1_700_000_000_000, suggestionsBlocked = true),
                PlayerSummaryDto("other", null, emptyList(), 1_600_000_000_000),
            ),
            nextCursor = "MTcwMDAwMDAwMC4wfG90aGVy",
        )
        assertEquals(page, json.decodeFromString(json.encodeToString(page)))
        val last = PlayerSearchPageDto(emptyList())
        assertEquals(last, json.decodeFromString(json.encodeToString(last)))

        val delete = DeletePlayerRequest(id)
        assertEquals(delete, json.decodeFromString(json.encodeToString(delete)))
        val ended = EndSessionsResultDto(3)
        assertEquals(ended, json.decodeFromString(json.encodeToString(ended)))
    }

    @Test
    fun noPlayerDtoCarriesASubjectOrToken() {
        val encoded = json.encodeToString(detail).lowercase()
        for (marker in listOf("subject", "token", "hash")) assertFalse(marker in encoded, "'$marker' in $encoded")
    }

    @Test
    fun aQueryRoundTripsThroughItsParameters() {
        val query = PlayerSearchQuery(
            q = "  ali ",
            type = PlayerType.GOOGLE,
            createdFrom = LocalDate(2026, 9, 1),
            createdTo = LocalDate(2026, 9, 18),
            blocked = true,
            cursor = "abc",
            size = 25,
        )
        val parameters = query.toQueryParameters()
        assertEquals(
            listOf("q" to "ali", "type" to "google", "createdFrom" to "2026-09-01", "createdTo" to "2026-09-18", "blocked" to "true", "cursor" to "abc", "size" to "25"),
            parameters,
        )
        val parsed = PlayerSearchQuery.fromQueryParameters(parameters.toMap())
        assertEquals(query.copy(q = "ali"), parsed.query)
        assertEquals(emptyList(), parsed.invalid)

        // Defaults are left out, and an empty query parses back to the defaults.
        assertEquals(emptyList(), PlayerSearchQuery().toQueryParameters())
        assertEquals(PlayerSearchQuery(), PlayerSearchQuery.fromQueryParameters(emptyMap()).query)
        assertEquals(PlayerSearchQuery(blocked = false), PlayerSearchQuery.fromQueryParameters(mapOf("blocked" to "false", "q" to " ")).query)
    }

    @Test
    fun malformedParametersAreNamedAndLeftAtTheirDefaults() {
        val parsed = PlayerSearchQuery.fromQueryParameters(
            mapOf("type" to "facebook", "createdFrom" to "2026-02-30", "createdTo" to "yesterday", "blocked" to "yes", "size" to "ten", "q" to "Vali"),
        )
        assertEquals(PlayerSearchQuery(q = "Vali"), parsed.query)
        assertEquals(
            listOf("type", "createdFrom", "createdTo", "blocked", "size"),
            parsed.invalid.map { it.field },
        )
        assertTrue(parsed.invalid.all { it.reason == FieldReasons.INVALID })
        assertEquals(PlayerType.ANONYMOUS, PlayerSearchQuery.fromQueryParameters(mapOf("type" to "ANONYMOUS")).query.type)
    }

    @Test
    fun anAccountIdIsAnExactMatchAndANameNeedsTwoCharacters() {
        val byId = PlayerSearchQuery(q = " ${id.uppercase()} ")
        assertEquals(id, byId.accountId)
        assertNull(byId.namePart)
        assertEquals(emptyList(), byId.problems())

        val byName = PlayerSearchQuery(q = "al")
        assertNull(byName.accountId)
        assertEquals("al", byName.namePart)
        assertEquals(emptyList(), byName.problems())

        assertEquals(listOf(FieldError("q", PlayerReasons.TOO_SHORT)), PlayerSearchQuery(q = " a ").problems())
        assertEquals(emptyList(), PlayerSearchQuery(q = "  ").problems())
        assertEquals(listOf(FieldError("size", FieldReasons.INVALID)), PlayerSearchQuery(size = PlayerParams.MAX_SIZE + 1).problems())
        assertFalse(PlayerSearchQuery.isAccountId("3f1c2a9e-7b4d-4c1a-9f0e"))
    }

    @Test
    fun playerRoutesPermissionsAndAuditActions() {
        assertEquals("/api/v1/admin/players", AdminRoutes.PLAYERS)
        assertEquals("/api/v1/admin/players/$id", AdminRoutes.player(id))
        assertEquals("/api/v1/admin/players/$id/delete", AdminRoutes.playerDelete(id))
        assertEquals("/api/v1/admin/players/$id/end-sessions", AdminRoutes.playerEndSessions(id))
        assertEquals("/api/v1/admin/players/$id/suggestion-block", AdminRoutes.playerSuggestionBlock(id))
        assertEquals("/api/v1/admin/players/$id/display-name", AdminRoutes.playerDisplayName(id))
        assertTrue(Permission.PLAYERS_READ in Permission.entries && Permission.PLAYERS_WRITE in Permission.entries)
        assertTrue(AuditActions.ALL.filter { it.startsWith(AuditActions.PLAYER_PREFIX) }.size == 5)
    }
}
