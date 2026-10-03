package uz.abumme.harfgame

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    // Unconfined so the off-main seed runs synchronously in the test
    private val eagerScope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun palette_persists_observes_and_overwrites() {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe, eagerScope)

        settings.setPaletteId("blueprint")
        assertEquals("blueprint", settings.paletteId.value, "observes the new value")

        // a fresh AppSettings over the same store = a relaunch
        val relaunched = AppSettings(ksafe, eagerScope)
        assertEquals("blueprint", relaunched.paletteId.value, "persists across relaunch")

        settings.setPaletteId("press")
        assertEquals("press", settings.paletteId.value, "overwrite replaces prior value")
    }

    @Test
    fun theme_mode_defaults_to_system_and_persists() {
        // the JVM store is shared with earlier runs and the desktop app: start from, and leave, the default
        val ksafe = KSafe()
        ksafe.putDirect("app.themeMode", ThemeMode.System.name)
        try {
            val settings = AppSettings(ksafe, eagerScope)
            assertEquals(ThemeMode.System, settings.themeMode.value, "defaults to following the platform")

            settings.setThemeMode(ThemeMode.Dark)
            assertEquals(ThemeMode.Dark, settings.themeMode.value, "observes the new value")
            assertEquals(ThemeMode.Dark, AppSettings(ksafe, eagerScope).themeMode.value, "persists across relaunch")

            settings.setThemeMode(ThemeMode.Light)
            assertEquals(ThemeMode.Light, AppSettings(ksafe, eagerScope).themeMode.value, "overwrite replaces prior value")
        } finally {
            ksafe.putDirect("app.themeMode", ThemeMode.System.name)
        }
    }
}
