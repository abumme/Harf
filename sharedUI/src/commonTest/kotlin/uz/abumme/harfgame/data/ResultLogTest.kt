package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.data.sync.RoundKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultLogTest {

    // distinctive so persisted KSafe state from other runs can't interfere
    private val rec = ResultRecord("test-lang", 987654L, won = true, attempts = 4)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacy_snapshots_and_results_deserialize_as_normal_official_rounds() {
        val legacyRoundJson = """{"version":1,"languageId":"en","puzzleDay":100,"rows":[],"current":[]}"""
        val decodedRound = json.decodeFromString<InProgressRound>(legacyRoundJson)
        assertEquals("en", decodedRound.languageId)
        assertEquals(100L, decodedRound.puzzleDay)
        assertEquals(RoundKind.OFFICIAL, decodedRound.roundKind)
        assertFalse(decodedRound.hardMode)

        val legacyResultJson = """{"language":"en","puzzleDay":100,"won":true,"attempts":3}"""
        val decodedResult = json.decodeFromString<ResultRecord>(legacyResultJson)
        assertEquals("en", decodedResult.language)
        assertEquals(100L, decodedResult.puzzleDay)
        assertEquals(true, decodedResult.won)
        assertEquals(3, decodedResult.attempts)
        assertEquals(RoundKind.OFFICIAL, decodedResult.roundKind)
        assertFalse(decodedResult.hardMode)
    }

    @Test
    fun record_survives_reopen_and_dedups() = runTest {
        try {
            val ksafe = KSafe()
            val log = ResultLog(ksafe)
            log.record(rec)
            log.record(rec) // duplicate language-day ignored

            assertEquals(1, log.all().count { it == rec }, "duplicate ignored")

            val reopened = ResultLog(ksafe)
            assertTrue(reopened.all().any { it == rec }, "survives re-open")
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun round_store_saves_restores_and_clears_by_day() = runTest {
        val store = RoundStore(KSafe())
        val round = InProgressRound(
            languageId = "test-lang",
            puzzleDay = 555L,
            rows = listOf(InProgressRow(listOf("a", "b"), listOf(0, 1))),
            current = listOf("c"),
        )
        store.save(round)
        assertEquals(round, store.load("test-lang", 555L))
        assertNull(store.load("test-lang", 556L), "different day = fresh round")
        store.clear("test-lang")
        assertNull(store.load("test-lang", 555L), "cleared")
    }

    @Test
    fun round_store_keeps_scripts_independent() = runTest {
        val store = RoundStore(KSafe())
        val latn = InProgressRound(languageId = "uz-latn", puzzleDay = 700L, rows = listOf(InProgressRow(listOf("k"), listOf(0))), current = listOf())
        val cyrl = InProgressRound(languageId = "uz-cyrl", puzzleDay = 700L, rows = listOf(), current = listOf("к"))
        store.save(latn)
        store.save(cyrl)
        assertEquals(latn, store.load("uz-latn", 700L), "latn kept after saving cyrl")
        assertEquals(cyrl, store.load("uz-cyrl", 700L), "cyrl kept")
        store.clear("uz-latn")
        assertNull(store.load("uz-latn", 700L), "latn cleared")
        assertEquals(cyrl, store.load("uz-cyrl", 700L), "clearing one script keeps the other")
    }
}
