package uz.abumme.harfgame.backend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.calendar.CalendarDays
import uz.abumme.harfgame.backend.admin.calendar.DailySettings
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.wordpack.WordPackIntegrity
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.lang.LaunchLanguages
import java.time.Instant
import kotlin.random.Random
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy drives the loop interval in virtual time
class ApplicationWiringTest {

    @Test
    fun aFreshDatabaseStartsWithPacksEqualToTheSeededContentAndEveryPackCanBePublished() = runBlocking {
        resetSuggestionData()
        val packs = WordPackServerService() // the deployed resources
        packs.seed()
        val seeded = packs.languages().associateWith { packs.getPack(it)!! }
        assertEquals(setOf("en", "kk", "ru", "uz-cyrl", "uz-latn"), seeded.keys)
        transaction(DatabaseFactory.init()) {
            clearDailyCalendars()
            WordsTable.deleteAll()
            WordPacksTable.deleteAll()
        }

        // Startup order: seed -> carry-over -> merge of the deployed dictionaries.
        val catalog = WordCatalogService(packs)
        prepareWordCatalog(packs, catalog)
        assertEquals(seeded, packs.languages().associateWith { packs.getPack(it)!! })

        // A restart changes nothing.
        prepareWordCatalog(packs, catalog)
        assertEquals(seeded, packs.languages().associateWith { packs.getPack(it)!! })

        // The real packs, rebuilt from the catalog, pass the app's integrity check: a change publishes version 2.
        val admin = StaffPrincipal(insertStaff("boss", Role.ADMIN), "boss", null, Role.ADMIN, emptySet(), "s")
        val newWords = mapOf("en" to "qxzvk", "ru" to "щъщъщ", "kk" to "ққққ", "uz-latn" to "qxqxq", "uz-cyrl" to "ққққ")
        for ((lang, word) in newWords) {
            catalog.add(admin, lang, word)
            val published = packs.getPack(lang)!!
            assertEquals("2", published.version, lang)
            assertTrue(word in published.guesses, lang)
            assertEquals(seeded.getValue(lang).schedule, published.schedule, lang)
            assertEquals(seeded.getValue(lang).answers.toSet(), published.answers.toSet(), lang)
        }
    }

    @Test
    fun theCalendarsFirstStartOnTheDeployedPacksKeepsEveryDevicesTodayAndTomorrow() = runBlocking {
        resetSuggestionData()
        val packs = WordPackServerService() // the deployed resources
        val clock = MutableClock(Instant.parse("2026-09-17T20:30:00Z")) // 23:30 in Moscow, already the 18th in Asia
        val catalog = WordCatalogService(packs, clock, daily = DailySettings(random = Random(1)))
        prepareWordCatalog(packs, catalog)
        val before = packs.languages().associateWith { packs.getPack(it)!! }

        prepareDailyCalendars(catalog)

        for ((lang, old) in before) {
            val published = packs.getPack(lang)!!
            val today = CalendarDays.today(DailyCalendars.calendarOf(lang)!!, clock.now)
            for (day in listOf(today, today.plusDays(1))) {
                assertEquals(
                    WordPackSchedule.answerFor(old.schedule, old.anchorEpochDay, day.toEpochDay()),
                    WordPackSchedule.answerFor(published.schedule, published.anchorEpochDay, day.toEpochDay()),
                    "$lang $day",
                )
            }
            assertTrue(WordPackIntegrity.check(published, LaunchLanguages.all.getValue(lang)) is WordPackIntegrity.Valid, lang)
            assertEquals(old.answers.toSet(), published.answers.toSet(), "$lang: the pool is the previous answers")
            assertEquals(today.plusDays(60).toEpochDay() - published.anchorEpochDay + 1, published.schedule.size.toLong(), lang)
        }

        // A restart changes nothing.
        val versions = packs.languages().associateWith { packs.getPack(it)!!.version }
        prepareWordCatalog(packs, catalog)
        prepareDailyCalendars(catalog)
        assertEquals(versions, packs.languages().associateWith { packs.getPack(it)!!.version })
    }

    @Test
    fun wordLookupStaysOnUnlessExplicitlyDisabled() {
        assertTrue(wordLookupEnabled(null)) // variable not set
        assertTrue(wordLookupEnabled("")) // present but blank in .env
        assertTrue(wordLookupEnabled("true"))
        assertFalse(wordLookupEnabled("false"))
        assertFalse(wordLookupEnabled(" FALSE "))
    }

    @Test
    fun supervisedLoopRunsOnItsIntervalAndSurvivesAFailure() = runTest {
        var runs = 0
        backgroundScope.superviseForever(intervalMillis = 1_000) {
            runs++
            if (runs == 1) throw IOException("transient database hiccup")
        }
        advanceTimeBy(2_500) // runs at 0 ms (fails), 1000 ms and 2000 ms
        assertEquals(3, runs)
    }
}
