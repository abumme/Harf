package uz.abumme.harfgame.backend.admin

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditQueryTest {
    private val now = Instant.now()

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    /** Writes an entry [daysAgo] days before now, as a word change of a later change would. */
    private fun entry(actor: String, action: String, lang: String?, daysAgo: Long) {
        val clock = MutableClock(now.minus(Duration.ofDays(daysAgo)))
        transaction(DatabaseFactory.init()) { AuditLog(clock).record(AuditActor.Staff(actor), action, lang = lang) }
    }

    private fun path(vararg params: Pair<String, Any>) =
        AdminRoutes.AUDIT + params.joinToString("&", prefix = "?") { (k, v) -> "$k=$v" }

    @Test
    fun adminFiltersByActorLanguageAndDateRangeNewestFirst() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val azizId = insertStaff("aziz", languages = listOf("ru", "kk"))
        val olimId = insertStaff("olim", languages = listOf("ru"), status = StaffStatus.DISABLED)
        entry(azizId, "WORD_ADDED", "ru", daysAgo = 1)
        entry(azizId, "WORD_ADDED", "ru", daysAgo = 3)
        entry(azizId, "WORD_ADDED", "kk", daysAgo = 2)
        entry(azizId, "WORD_ADDED", "ru", daysAgo = 10)
        entry(olimId, "WORD_ADDED", "ru", daysAgo = 2)
        val client = adminClient()
        val boss = client.signIn("boss")

        val from = now.minus(Duration.ofDays(7)).toEpochMilli()
        val to = now.toEpochMilli()
        val page = client.get(path("actor" to azizId, "lang" to "ru", "from" to from, "to" to to)) { withSession(boss) }
            .body<PageDto<AuditEntryDto>>()
        assertEquals(2, page.total)
        assertEquals(listOf(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(3))).map { it.toEpochMilli() }, page.items.map { it.at })
        assertTrue(page.items.all { it.actorStaffId == azizId && it.actorUsername == "aziz" && it.lang == "ru" })

        // A disabled WORDER's history is still listed with them as actor.
        val olim = client.get(path("actor" to olimId)) { withSession(boss) }.body<PageDto<AuditEntryDto>>()
        assertEquals(listOf("olim"), olim.items.map { it.actorUsername })

        // Action filter and paging over everything (the ADMIN's own sign-in included).
        val words = client.get(path("action" to "WORD_ADDED", "size" to 2, "page" to 1)) { withSession(boss) }.body<PageDto<AuditEntryDto>>()
        assertEquals(5, words.total)
        assertEquals(2, words.items.size)
        assertEquals(1, words.page)
        val all = client.get(AdminRoutes.AUDIT) { withSession(boss) }.body<PageDto<AuditEntryDto>>()
        assertEquals(6, all.total)
        assertEquals(all.items.sortedByDescending { it.at }, all.items)

        assertEquals(HttpStatusCode.UnprocessableEntity, client.get(path("page" to "x")) { withSession(boss) }.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get(path("size" to 1000)) { withSession(boss) }.status)
    }

    @Test
    fun worderSeesOnlyTheirOwnEntriesAndCannotNameAnotherActor() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        val azizId = insertStaff("aziz", languages = listOf("ru"))
        val olimId = insertStaff("olim", languages = listOf("ru"))
        entry(azizId, "WORD_ADDED", "ru", daysAgo = 1)
        entry(olimId, "WORD_ADDED", "ru", daysAgo = 1)
        entry(bossId, "STAFF_CREATED", null, daysAgo = 1)
        val client = adminClient()
        val aziz = client.signIn("aziz")

        val own = client.get(AdminRoutes.AUDIT) { withSession(aziz) }.body<PageDto<AuditEntryDto>>()
        assertTrue(own.items.isNotEmpty())
        assertTrue(own.items.all { it.actorStaffId == azizId }, own.items.toString())
        assertEquals(2, own.total, "the word change and the sign-in")

        val explicitSelf = client.get(path("actor" to azizId, "lang" to "ru")) { withSession(aziz) }.body<PageDto<AuditEntryDto>>()
        assertEquals(1, explicitSelf.total)

        val other = client.get(path("actor" to olimId)) { withSession(aziz) }
        assertEquals(HttpStatusCode.Forbidden, other.status)
        assertEquals("forbidden", other.error().error)
    }
}
