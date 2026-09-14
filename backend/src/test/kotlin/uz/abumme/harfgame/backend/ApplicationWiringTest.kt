package uz.abumme.harfgame.backend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy drives the loop interval in virtual time
class ApplicationWiringTest {

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
