package uz.abumme.harfgame.backend.admin.players

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerLanguageStatsDto
import uz.abumme.harfgame.data.admin.players.SuggestionCountsDto
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.stats.Streaks
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PlayerDetailTest {

    // 21:30 in Moscow and 23:30 in Tashkent: the same instant is one puzzle day for every language here.
    private val clock = MutableClock(Instant.parse("2026-09-16T18:30:00Z"))
    private val service = PlayersService(clock, AuditLog(clock), AuthServerService(JwtService()))

    @BeforeTest
    fun setup() = resetPlayerData()

    private fun detail(id: String): PlayerDetailDto? = runBlocking { service.detail(id) }

    private fun today(lang: String): Long = PuzzleDays.epochDay(lang, kotlin.time.Instant.fromEpochMilliseconds(clock.millis()))

    private fun won(lang: String, day: Long, attempts: Int = 3) = ResultRecordDto(lang, day, true, attempts)
    private fun lost(lang: String, day: Long) = ResultRecordDto(lang, day, false, 6)

    @Test
    fun aThreeDayRunEndingYesterdayIsACurrentStreakOfThree() {
        val today = today("en")
        val records = listOf(won("en", today - 3), won("en", today - 2), won("en", today - 1), lost("en", today - 5))
        val id = insertPlayer("Grace")
        insertStats(id, records)

        val en = detail(id)!!.languages.single()

        assertEquals(PlayerLanguageStatsDto("en", games = 4, wins = 3, winRate = 0.75f, currentStreak = 3, bestStreak = 3), en)
        // Exactly what the app's stats screen computes for the same records.
        assertEquals(Streaks.streak(records, "en", today).current, en.currentStreak)
    }

    @Test
    fun aLapsedStreakIsZeroButTheBestIsKept() {
        val today = today("en")
        val id = insertPlayer()
        insertStats(id, listOf(won("en", today - 6), won("en", today - 5), won("en", today - 4), won("en", today - 2)))

        val en = detail(id)!!.languages.single()
        assertEquals(0, en.currentStreak)
        assertEquals(3, en.bestStreak)
    }

    @Test
    fun eachLanguageIsJudgedByItsOwnPuzzleDay() {
        // At 20:30Z Moscow is still on the 16th while Tashkent is on the 17th, so "yesterday" differs per language.
        clock.now = Instant.parse("2026-09-16T20:30:00Z")
        val ruToday = today("ru")
        val enToday = today("en")
        assertEquals(enToday - 1, ruToday)
        val id = insertPlayer()
        insertStats(id, listOf(won("ru", ruToday - 1), won("en", ruToday - 1)))

        val languages = detail(id)!!.languages.associateBy { it.lang }
        assertEquals(1, languages.getValue("ru").currentStreak)
        assertEquals(0, languages.getValue("en").currentStreak, "for English that win was two puzzle days ago")
        assertEquals(listOf("en", "ru"), detail(id)!!.languages.map { it.lang })
    }

    @Test
    fun anAccountWithoutStatsHasNoSnapshotAndAnEmptySummary() {
        val id = insertPlayer(createdAt = Instant.parse("2026-09-02T10:00:00Z"))

        val detail = detail(id)!!
        assertNull(detail.lastStatsSnapshotAt)
        assertEquals(emptyList(), detail.languages)
        assertEquals(Instant.parse("2026-09-02T10:00:00Z").toEpochMilli(), detail.createdAt)
        assertEquals(SuggestionCountsDto(), detail.suggestionCounts)
        assertEquals(0, detail.activeSessions)
        assertNull(detail.suggestionBlock)
    }

    @Test
    fun theSnapshotTimeIsTheAcceptedSnapshots() {
        val id = insertPlayer()
        insertStats(id, emptyList(), updatedAt = Instant.parse("2026-09-15T12:00:00Z"))
        assertEquals(Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(), detail(id)!!.lastStatsSnapshotAt)
    }

    @Test
    fun onlyLiveUnrotatedTokensAreActiveSessions() {
        val id = insertPlayer()
        val future = clock.now.plus(Duration.ofDays(10))
        val head = insertRefreshToken(id, expiresAt = future)
        insertRefreshToken(id, expiresAt = future, replacedBy = head) // rotated
        insertRefreshToken(id, expiresAt = future, revokedAt = clock.now.minusSeconds(60)) // revoked
        insertRefreshToken(id, expiresAt = clock.now.minusSeconds(1)) // expired
        insertRefreshToken(id, expiresAt = future) // a second device

        assertEquals(2, detail(id)!!.activeSessions)
    }

    @Test
    fun suggestionCountsRecentSuggestionsAndTheBlock() {
        val bossId = insertStaff("boss", Role.ADMIN)
        val id = insertPlayer("Grace", blockedAt = Instant.parse("2026-09-14T09:00:00Z"), blockedBy = bossId)
        val t0 = Instant.parse("2026-09-01T00:00:00Z")
        repeat(22) { n -> insertSuggestion("en", "word$n", createdAt = t0.plusSeconds(n * 60L), author = id) }
        insertSuggestion("ru", "берег", SuggestionStatus.ACCEPTED, decidedAt = t0.plusSeconds(3600), createdAt = t0.plusSeconds(30 * 60), author = id)
        insertSuggestion("ru", "река", SuggestionStatus.REJECTED, decidedAt = t0.plusSeconds(3600), createdAt = t0.plusSeconds(31 * 60), author = id)
        insertSuggestion("en", "other", author = null)

        val detail = detail(id)!!

        assertEquals(SuggestionCountsDto(pending = 22, accepted = 1, rejected = 1), detail.suggestionCounts)
        assertEquals(20, detail.recentSuggestions.size)
        assertEquals(listOf("река", "берег", "word21"), detail.recentSuggestions.take(3).map { it.word })
        assertEquals(t0.plusSeconds(3600).toEpochMilli(), detail.recentSuggestions.first().decidedAt)
        assertEquals(SuggestionStatus.REJECTED, detail.recentSuggestions.first().status)
        assertEquals(Instant.parse("2026-09-14T09:00:00Z").toEpochMilli(), detail.suggestionBlock!!.blockedAt)
        assertEquals("boss", detail.suggestionBlock!!.blockedBy.username)
        assertEquals(bossId, detail.suggestionBlock!!.blockedBy.id)
    }

    @Test
    fun anUnknownAccountIsNull() {
        assertNull(detail("3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"))
        assertNull(detail("not-an-id"))
    }

    @Test
    fun theDetailNeverCarriesProviderSubjectsOrTokens() {
        val id = insertPlayer(
            "Grace",
            identities = listOf(OAuthProvider.GOOGLE to "google-subject-7788", OAuthProvider.APPLE to "apple-subject-9911"),
        )
        insertRefreshToken(id, expiresAt = clock.now.plus(Duration.ofDays(1)), tokenHash = "tokenhash0123456789abcdef")

        val detail = detail(id)!!
        val json = Json.encodeToString(detail)

        assertEquals(listOf(OAuthProvider.GOOGLE, OAuthProvider.APPLE), detail.providers)
        assertEquals(1, detail.activeSessions)
        for (secret in listOf("google-subject-7788", "apple-subject-9911", "tokenhash0123456789abcdef")) {
            assertFalse(secret in json, "$secret in $json")
        }
        assertTrue(auditRows().isEmpty(), "viewing is not audited")
    }
}
