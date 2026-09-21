package uz.abumme.harfgame.backend.admin.calendar

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import uz.abumme.harfgame.data.wordpack.WordPackIntegrity
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.lang.LaunchLanguages
import java.sql.Connection
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CalendarMigrationTest {
    private val clock = MutableClock(CALENDAR_NOW)
    private val languages = listOf("en", "kk", "ru", "uz-cyrl", "uz-latn")
    private val anchor = LocalDate.ofEpochDay(WordPackSchedule.ANCHOR_EPOCH_DAY)

    @BeforeTest
    fun setup() = resetCalendarData()

    private fun todayOf(lang: String) =
        LocalDate.ofEpochDay(PuzzleDays.epochDay(lang, kotlin.time.Instant.fromEpochMilliseconds(clock.now.toEpochMilli())))

    @Test
    fun theFirstStartKeepsTodayAndTomorrowAndFillsSixtyDaysAhead() {
        val before = languages.associateWith { pack(it) }
        val catalog = calendarCatalog(clock)

        val reports = migrateCalendars(catalog)

        assertEquals(DailyCalendars.ALL.sorted(), reports.map { it.calendar })
        for (lang in languages) {
            val today = todayOf(lang)
            val published = pack(lang)
            for (day in listOf(today, today.plusDays(1))) {
                assertEquals(before.getValue(lang).wordOn(day), published.wordOn(day), "$lang $day is unchanged")
            }
            // Every legacy day keeps its word too.
            assertEquals(before.getValue(lang).schedule.take(published.schedule.size - 59), published.schedule.take(published.schedule.size - 59), lang)
            assertEquals("2", published.version, lang)
            assertEquals(WordPackSchedule.ANCHOR_EPOCH_DAY, published.anchorEpochDay)
            assertEquals(today.plusDays(2).toEpochDay(), published.effectiveFrom, "$lang takes effect the day after tomorrow")
            assertEquals((today.plusDays(60).toEpochDay() - anchor.toEpochDay() + 1).toInt(), published.schedule.size, "$lang reaches today + 60")
            val config = LaunchLanguages.all.getValue(lang)
            assertTrue(WordPackIntegrity.check(published, config) is WordPackIntegrity.Valid, lang)
        }

        for (calendar in DailyCalendars.ALL) {
            val rows = calendarRows(calendar)
            val today = CalendarDays.today(calendar, clock.now)
            assertEquals(anchor, rows.first()[DailyWordsTable.day])
            assertTrue(rows.filter { it[DailyWordsTable.day] <= today.plusDays(1) }.all { it[DailyWordsTable.daySource] == DaySource.LEGACY.name })
            assertTrue(rows.filter { it[DailyWordsTable.day] > today.plusDays(1) }.all { it[DailyWordsTable.daySource] == DaySource.AUTO.name })
            assertEquals(today.plusDays(60), rows.last()[DailyWordsTable.day])
        }
        // Legacy days point at their catalog words; Uzbek days at their pairs.
        assertTrue(calendarRows("en").all { it[DailyWordsTable.wordId] != null })
        assertTrue(calendarRows("uz").all { it[DailyWordsTable.lexemeId] != null && it[DailyWordsTable.textCyrl] != null })
    }

    @Test
    fun theAnswerPoolStartsAsThePreviousAnswers() {
        migrateCalendars(calendarCatalog(clock))

        assertEquals(EN_POOL.toSet(), eligibleTexts("en"))
        assertEquals(RU_POOL.toSet(), eligibleTexts("ru"))
        assertEquals(KK_POOL.toSet(), eligibleTexts("kk"))
        assertEquals(UZ_PAIRS.map { it.first }.toSet(), eligibleTexts("uz-latn"))
        assertEquals(UZ_PAIRS.map { it.second }.toSet(), eligibleTexts("uz-cyrl"))
        assertEquals(UZ_PAIRS.size.toLong(), transaction(DatabaseFactory.init()) { LexemePairsTable.selectAll().count() })
        assertEquals(EN_POOL.sorted(), pack("en").answers)
        assertEquals(UZ_PAIRS.map { it.second }.sorted(), pack("uz-cyrl").answers)
    }

    @Test
    fun aSecondStartChangesNothing() {
        val catalog = calendarCatalog(clock)
        migrateCalendars(catalog)
        val versions = languages.associateWith { pack(it).version }
        val snapshots = DailyCalendars.ALL.associateWith { calendarSnapshot(it) }

        clock.advance(java.time.Duration.ofMinutes(5))
        assertEquals(emptyList(), migrateCalendars(calendarCatalog(clock)))

        assertEquals(versions, languages.associateWith { pack(it).version })
        assertEquals(snapshots, DailyCalendars.ALL.associateWith { calendarSnapshot(it) })
    }

    @Test
    fun aNoOpReconcileWritesNothingAndPublishesNothing() = runBlocking {
        val catalog = calendarCatalog(clock)
        migrateCalendars(catalog)
        val versions = languages.associateWith { pack(it).version }
        val snapshots = DailyCalendars.ALL.associateWith { calendarSnapshot(it) }
        val calendars = CalendarService(catalog)

        clock.advance(java.time.Duration.ofMinutes(3))
        for (calendar in DailyCalendars.ALL) {
            assertTrue(calendars.refresh(calendar, clock.now, dayChanged = true), calendar)
            val changed = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                DailyCalendars.packLanguages(calendar).forEach { catalog.publisher.lock(it) }
                catalog.calendars.reconcile(calendar, clock.now)
            }
            assertEquals(false, changed, calendar)
        }

        assertEquals(snapshots, DailyCalendars.ALL.associateWith { calendarSnapshot(it) })
        assertEquals(versions, languages.associateWith { pack(it).version })
    }

    @Test
    fun uzbekDaysPublishOneLexemeInBothScripts() {
        migrateCalendars(calendarCatalog(clock))
        val latn = pack("uz-latn")
        val cyrl = pack("uz-cyrl")
        assertEquals(latn.schedule.size, cyrl.schedule.size)
        val pairs = UZ_PAIRS.toMap()
        latn.schedule.zip(cyrl.schedule).forEach { (l, c) -> assertEquals(pairs.getValue(l), c) }
        assertEquals(UZ_PAIRS.map { it.first }.sorted(), latn.answers)
    }

    @Test
    fun theHistoryStartSettingDecidesWhatCountsAsUsed() {
        // Launch in the future: every legacy day is test-period history, so the first open days get never-used words.
        migrateCalendars(calendarCatalog(clock, DailySettings(historyStart = TODAY.plusDays(2), random = kotlin.random.Random(3))))
        val rows = calendarRows("en").filter { it[DailyWordsTable.day] >= TODAY.plusDays(2) }
        assertEquals(EN_POOL.size, rows.take(EN_POOL.size).count { !it[DailyWordsTable.isRepeat] })
        assertEquals(EN_POOL.toSet(), rows.take(EN_POOL.size).map { it[DailyWordsTable.text] }.toSet())
    }
}
