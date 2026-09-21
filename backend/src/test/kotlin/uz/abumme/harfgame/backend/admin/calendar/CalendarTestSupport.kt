package uz.abumme.harfgame.backend.admin.calendar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.AdminConfig
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.testHasher
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.clearDailyCalendars
import uz.abumme.harfgame.backend.db.CalendarNoticesTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

// Fixtures of the daily-word calendar tests.

/** 10:00 UTC on 2026-09-17: 15:00 in Tashkent and Almaty, 13:00 in Moscow, the same day in every calendar. */
val CALENDAR_NOW: Instant = Instant.parse("2026-09-17T10:00:00Z")
val TODAY: LocalDate = LocalDate.parse("2026-09-17")

val EN_POOL = listOf("apple", "bread", "chair", "dance", "eagle", "house", "plant", "water")
val RU_POOL = listOf("слово", "книга", "город", "время", "стена", "песня")
val KK_POOL = listOf("кітап", "қалам", "балық", "орман", "жүрек")

/** The deployed `uz_lexemes.tsv`, which the migration pairs. */
val UZ_PAIRS = listOf("kitob" to "китоб", "bahor" to "баҳор", "shahar" to "шаҳар", "qalam" to "қалам", "bulut" to "булут")

/** Catalog words that are not answers: candidates for the pool (and a 3-letter legacy word no board can hold). */
val EN_EXTRA = listOf("lemon", "melon", "grape", "tiger", "oak")
val UZ_LATN_EXTRA = listOf("daryo", "osmon", "bolta")
val UZ_CYRL_EXTRA = listOf("осмон", "болта")

/**
 * Leaves one small version-1 pack per launch language, as the server seeded them (answers, the generated 800-day
 * schedule from the anchor, guesses = answers + extras), carried into the catalog; clears staff, audit, suggestions and
 * the calendars. No calendar is initialized yet.
 */
fun resetCalendarData() {
    transaction(DatabaseFactory.init()) {
        StaffAuditLogTable.deleteAll()
        clearDailyCalendars()
        WordsTable.deleteAll()
        WordSuggestionsTable.deleteAll()
        StaffSessionsTable.deleteAll()
        StaffLanguagesTable.deleteAll()
        StaffTable.deleteAll()
        WordPacksTable.deleteAll()
        val order = WordPackSchedule.buildOrder(UZ_PAIRS.size, WordPackSchedule.seedFor("uz"))
        insertSeededPack("en", EN_POOL, EN_POOL + EN_EXTRA, WordPackSchedule.build(EN_POOL, WordPackSchedule.seedFor("en")))
        insertSeededPack("ru", RU_POOL, RU_POOL, WordPackSchedule.build(RU_POOL, WordPackSchedule.seedFor("ru")))
        insertSeededPack("kk", KK_POOL, KK_POOL, WordPackSchedule.build(KK_POOL, WordPackSchedule.seedFor("kk")))
        insertSeededPack("uz-latn", UZ_PAIRS.map { it.first }, UZ_PAIRS.map { it.first } + UZ_LATN_EXTRA, order.map { UZ_PAIRS[it].first })
        insertSeededPack("uz-cyrl", UZ_PAIRS.map { it.second }, UZ_PAIRS.map { it.second } + UZ_CYRL_EXTRA, order.map { UZ_PAIRS[it].second })
    }
    runBlocking { WordCatalogService(WordPackServerService()).carryOver() }
    transaction(DatabaseFactory.init()) { StaffAuditLogTable.deleteAll() }
}

private fun insertSeededPack(lang: String, answers: List<String>, guesses: List<String>, schedule: List<String>) {
    WordPacksTable.insert {
        it[WordPacksTable.lang] = lang
        it[version] = "1"
        it[effectiveFrom] = WordPackSchedule.ANCHOR_EPOCH_DAY
        it[anchorEpochDay] = WordPackSchedule.ANCHOR_EPOCH_DAY
        it[WordPacksTable.answers] = Json.encodeToString(answers)
        it[WordPacksTable.guesses] = Json.encodeToString(guesses)
        it[WordPacksTable.schedule] = Json.encodeToString(schedule)
        it[updatedAt] = Instant.now()
    }
}

/** A catalog on [clock] with seeded automatic picks. */
fun calendarCatalog(clock: MutableClock, settings: DailySettings = DailySettings(random = Random(7))) =
    WordCatalogService(WordPackServerService(), clock, daily = settings)

/** An admin backend on [clock] whose catalog picks with a seeded random. */
fun calendarBackend(clock: MutableClock, settings: DailySettings = DailySettings(random = Random(7))): AdminBackend {
    val wordPacks = WordPackServerService()
    return AdminBackend(
        config = AdminConfig(loginAttemptsPerMinute = 1_000),
        packLanguages = { wordPacks.languages() },
        clock = clock,
        passwordHasher = testHasher,
        wordPacks = wordPacks,
        catalog = WordCatalogService(wordPacks, clock, daily = settings),
    )
}

/** Initializes every calendar at the catalog clock's time, the way startup does. */
fun migrateCalendars(catalog: WordCatalogService) = runBlocking { CalendarMigration(catalog).run() }

fun pack(lang: String): WordPackDto = runBlocking { WordPackServerService().getPack(lang)!! }

/** The published word of [day] in [pack], as the app resolves it. */
fun WordPackDto.wordOn(day: LocalDate): String = WordPackSchedule.answerFor(schedule, anchorEpochDay, day.toEpochDay())

/** A calendar's stored days, ascending. */
fun calendarRows(calendar: String): List<ResultRow> = transaction(DatabaseFactory.init()) {
    DailyWordsTable.selectAll().where { DailyWordsTable.calendar eq calendar }.orderBy(DailyWordsTable.day, SortOrder.ASC).toList()
}

fun dayRow(calendar: String, day: LocalDate): ResultRow? = transaction(DatabaseFactory.init()) {
    DailyWordsTable.selectAll().where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }.singleOrNull()
}

fun noticeRows(): List<ResultRow> = transaction(DatabaseFactory.init()) { CalendarNoticesTable.selectAll().toList() }

fun wordId(lang: String, text: String): String = transaction(DatabaseFactory.init()) {
    WordsTable.selectAll().where { (WordsTable.lang eq lang) and (WordsTable.text eq text) }.single()[WordsTable.id]
}

fun eligibleTexts(lang: String): Set<String> = transaction(DatabaseFactory.init()) {
    WordsTable.selectAll().where { (WordsTable.lang eq lang) and (WordsTable.dailyEligible eq true) }.mapTo(HashSet()) { it[WordsTable.text] }
}

/** Every column of every stored day, for comparing a calendar before and after. */
fun calendarSnapshot(calendar: String): List<List<Any?>> =
    calendarRows(calendar).map { row -> DailyWordsTable.columns.map { row[it] } }
