package uz.abumme.harfgame.backend.admin.players

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerType
import uz.abumme.harfgame.data.auth.OAuthProvider
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerSearchTest {

    private val clock = MutableClock()
    private val service = PlayersService(clock, AuditLog(clock), AuthServerService(JwtService()))
    private val t0 = Instant.parse("2026-09-01T08:00:00Z")

    @BeforeTest
    fun setup() = resetPlayerData()

    private fun search(query: PlayerSearchQuery): PlayerSearchPageDto = runBlocking { service.search(query) }

    private fun ids(query: PlayerSearchQuery): Set<String> = search(query).items.map { it.id }.toSet()

    private fun at(minutes: Long): Instant = t0.plusSeconds(minutes * 60)

    @Test
    fun anExactAccountIdFindsExactlyThatAccount() {
        val wanted = insertPlayer("Alisher", at(1))
        insertPlayer("Alisher", at(2))

        assertEquals(setOf(wanted), ids(PlayerSearchQuery(q = wanted)))
        assertEquals(setOf(wanted), ids(PlayerSearchQuery(q = " ${wanted.uppercase()} ")))
        // Other filters still apply to an id search.
        assertEquals(emptySet(), ids(PlayerSearchQuery(q = wanted, type = PlayerType.GOOGLE)))
    }

    @Test
    fun partOfADisplayNameMatchesInAnyCase() {
        val alisher = insertPlayer("Alisher", at(1))
        val vali = insertPlayer("Vali", at(2))
        insertPlayer("Bobur", at(3))
        insertPlayer(null, at(4))
        val cyrillic = insertPlayer("АЛИЯ", at(5))

        assertEquals(setOf(alisher, vali), ids(PlayerSearchQuery(q = "ali")))
        assertEquals(setOf(alisher, vali), ids(PlayerSearchQuery(q = "ALI")))
        assertEquals(setOf(cyrillic), ids(PlayerSearchQuery(q = "али")))
    }

    @Test
    fun percentAndUnderscoreInANameAreMatchedLiterally() {
        val percent = insertPlayer("a%b", at(1))
        insertPlayer("axxb", at(2))
        val underscore = insertPlayer("x_y", at(3))
        insertPlayer("xzy", at(4))
        val backslash = insertPlayer("c\\d", at(5))
        insertPlayer("cd", at(6))

        assertEquals(setOf(percent), ids(PlayerSearchQuery(q = "a%")))
        assertEquals(setOf(underscore), ids(PlayerSearchQuery(q = "x_")))
        assertEquals(setOf(backslash), ids(PlayerSearchQuery(q = "c\\")))
    }

    @Test
    fun accountTypesFilterByLinkedProviders() {
        val anonymous = insertPlayer(null, at(1))
        val google = insertPlayer("G", at(2), identities = listOf(OAuthProvider.GOOGLE to "g-1"))
        val apple = insertPlayer("A", at(3), identities = listOf(OAuthProvider.APPLE to "a-1"))
        val both = insertPlayer("B", at(4), identities = listOf(OAuthProvider.GOOGLE to "g-2", OAuthProvider.APPLE to "a-2"))

        assertEquals(setOf(anonymous), ids(PlayerSearchQuery(type = PlayerType.ANONYMOUS)))
        assertEquals(setOf(google, both), ids(PlayerSearchQuery(type = PlayerType.GOOGLE)))
        assertEquals(setOf(apple, both), ids(PlayerSearchQuery(type = PlayerType.APPLE)))
        assertEquals(setOf(anonymous, google, apple, both), ids(PlayerSearchQuery()))

        val items = search(PlayerSearchQuery()).items.associateBy { it.id }
        assertEquals(listOf(OAuthProvider.GOOGLE, OAuthProvider.APPLE), items.getValue(both).providers)
        assertEquals(emptyList(), items.getValue(anonymous).providers)
    }

    @Test
    fun creationDatesAreTashkentDaysFromInclusiveToExclusive() {
        // Tashkent is UTC+5: 18:59:59Z is still the 10th there, 19:00:00Z is already the 11th.
        val lastSecondOfThe10th = insertPlayer(null, Instant.parse("2026-09-10T18:59:59Z"))
        val firstSecondOfThe11th = insertPlayer(null, Instant.parse("2026-09-10T19:00:00Z"))
        val the12th = insertPlayer(null, Instant.parse("2026-09-12T10:00:00Z"))

        assertEquals(setOf(firstSecondOfThe11th, the12th), ids(PlayerSearchQuery(createdFrom = LocalDate(2026, 9, 11))))
        assertEquals(setOf(lastSecondOfThe10th), ids(PlayerSearchQuery(createdTo = LocalDate(2026, 9, 11))))
        assertEquals(
            setOf(firstSecondOfThe11th),
            ids(PlayerSearchQuery(createdFrom = LocalDate(2026, 9, 11), createdTo = LocalDate(2026, 9, 12))),
        )
    }

    @Test
    fun theBlockedFilterFollowsTheCurrentBlock() {
        val blocked = insertPlayer("Blocked", at(1), blockedAt = at(10), blockedBy = "staff-1")
        val free = insertPlayer("Free", at(2))

        assertEquals(setOf(blocked), ids(PlayerSearchQuery(blocked = true)))
        assertEquals(setOf(free), ids(PlayerSearchQuery(blocked = false)))
        assertEquals(true, search(PlayerSearchQuery(blocked = true)).items.single().suggestionsBlocked)
    }

    @Test
    fun filtersCombine() {
        val match = insertPlayer("Alisher", Instant.parse("2026-09-11T06:00:00Z"), identities = listOf(OAuthProvider.GOOGLE to "g-1"), blockedAt = at(1), blockedBy = "s")
        insertPlayer("Alisher", Instant.parse("2026-09-11T07:00:00Z"), identities = listOf(OAuthProvider.GOOGLE to "g-2"))
        insertPlayer("Alisher", Instant.parse("2026-09-11T08:00:00Z"), identities = listOf(OAuthProvider.APPLE to "a-1"), blockedAt = at(1), blockedBy = "s")
        insertPlayer("Alisher", Instant.parse("2026-09-09T06:00:00Z"), identities = listOf(OAuthProvider.GOOGLE to "g-3"), blockedAt = at(1), blockedBy = "s")
        insertPlayer("Vali", Instant.parse("2026-09-11T09:00:00Z"), identities = listOf(OAuthProvider.GOOGLE to "g-4"), blockedAt = at(1), blockedBy = "s")

        val query = PlayerSearchQuery(
            q = "alish",
            type = PlayerType.GOOGLE,
            createdFrom = LocalDate(2026, 9, 11),
            createdTo = LocalDate(2026, 9, 12),
            blocked = true,
        )
        assertEquals(setOf(match), ids(query))
    }

    @Test
    fun nothingMatchingIsAnEmptyLastPage() {
        insertPlayer("Alisher", at(1))
        val page = search(PlayerSearchQuery(q = "zz"))
        assertEquals(emptyList(), page.items)
        assertNull(page.nextCursor)
    }

    @Test
    fun aOneCharacterNameSearchIsRefused() {
        val refused = assertFailsWith<AdminApiException> { search(PlayerSearchQuery(q = " a ")) }
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertEquals(FieldError("q", PlayerReasons.TOO_SHORT).toMessage(), refused.message)
        assertEquals(HttpStatusCode.UnprocessableEntity, assertFailsWith<AdminApiException> { search(PlayerSearchQuery(size = 101)) }.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, assertFailsWith<AdminApiException> { search(PlayerSearchQuery(cursor = "not a cursor")) }.status)
    }

    @Test
    fun pagesAreNewestFirstAndNeitherRepeatNorSkipWhenAccountsArriveBetweenPages() {
        // Seven accounts, three sharing one creation time, so the id breaks ties.
        val existing = listOf(at(1), at(2), at(3), at(3), at(3), at(4), at(5)).map { insertPlayer(null, it) }

        val first = search(PlayerSearchQuery(size = 3))
        assertEquals(3, first.items.size)
        assertNotNull(first.nextCursor)
        val createdAt = first.items.map { it.createdAt }
        assertEquals(createdAt.sortedDescending(), createdAt)

        // A newer account arrives before the next page is asked for.
        insertPlayer(null, at(100))

        val second = search(PlayerSearchQuery(size = 3, cursor = first.nextCursor))
        val third = search(PlayerSearchQuery(size = 3, cursor = second.nextCursor))
        assertNull(third.nextCursor)

        val seen = (first.items + second.items + third.items).map { it.id }
        assertEquals(seen.distinct(), seen, "no account repeats")
        assertEquals(existing.toSet(), seen.toSet(), "no account that existed is skipped")
        val order = (first.items + second.items + third.items).map { it.createdAt to it.id }
        assertEquals(order.sortedWith(compareByDescending<Pair<Long, String>> { it.first }.thenByDescending { it.second }), order)
    }

    @Test
    fun searchingIsNotAudited() {
        insertPlayer("Alisher", at(1))
        search(PlayerSearchQuery(q = "ali"))
        assertTrue(auditRows().isEmpty())
    }
}
