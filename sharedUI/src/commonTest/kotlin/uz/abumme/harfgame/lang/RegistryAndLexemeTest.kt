package uz.abumme.harfgame.lang

import org.koin.dsl.koinApplication
import uz.abumme.harfgame.di.languageModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegistryAndLexemeTest {

    @Test
    fun registry_resolves_config_by_id() {
        val registry = LanguageRegistry()
        assertNotNull(registry.config("uz-latn"))
        assertEquals(setOf("uz-latn", "uz-cyrl", "ru", "en", "kk"), registry.ids)
        assertNull(registry.config("xx"))
    }

    @Test
    fun registry_is_provided_via_koin() {
        val app = koinApplication { modules(languageModule) }
        val registry = app.koin.get<LanguageRegistry>()
        assertNotNull(registry.config("kk"))
        app.close()
    }

    @Test
    fun uzbek_lexeme_resolves_per_script() {
        val lexeme = Lexeme(
            id = "city",
            perScript = mapOf(
                "uz-latn" to listOf("sh", "a", "h", "a", "r"),
                "uz-cyrl" to listOf("ш", "а", "ҳ", "а", "р"),
            ),
        )
        assertEquals(listOf("sh", "a", "h", "a", "r"), lexeme.graphemes("uz-latn"))
        assertEquals(listOf("ш", "а", "ҳ", "а", "р"), lexeme.graphemes("uz-cyrl"))
    }
}
