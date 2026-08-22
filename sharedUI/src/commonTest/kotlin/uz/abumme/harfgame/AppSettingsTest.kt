package uz.abumme.harfgame

import eu.anifantakis.lib.ksafe.KSafe
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    @Test
    fun palette_persists_observes_and_overwrites() {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)

        settings.setPaletteId("blueprint")
        assertEquals("blueprint", settings.paletteId.value, "observes the new value")

        // a fresh AppSettings over the same store = a relaunch
        val relaunched = AppSettings(ksafe)
        assertEquals("blueprint", relaunched.paletteId.value, "persists across relaunch")

        settings.setPaletteId("press")
        assertEquals("press", settings.paletteId.value, "overwrite replaces prior value")
    }
}
