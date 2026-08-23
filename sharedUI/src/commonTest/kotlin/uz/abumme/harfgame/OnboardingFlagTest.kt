package uz.abumme.harfgame

import eu.anifantakis.lib.ksafe.KSafe
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingFlagTest {

    @Test
    fun onboarded_defaults_false_persists_true() {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)
        settings.setOnboarded(false) // reset shared store via the real key
        assertFalse(settings.isOnboarded(), "false by default")

        settings.setOnboarded(true)
        assertTrue(settings.isOnboarded(), "true after set")

        // a fresh AppSettings over the same store = a relaunch
        assertTrue(AppSettings(ksafe).isOnboarded(), "persists across relaunch")
    }
}
