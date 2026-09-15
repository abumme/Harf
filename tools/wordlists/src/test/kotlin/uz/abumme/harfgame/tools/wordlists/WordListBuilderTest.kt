package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WordListBuilderTest {

    private lateinit var repo: Path
    private lateinit var dumps: Path

    @BeforeTest
    fun setup() {
        repo = Files.createTempDirectory("harf-repo")
        dumps = Files.createTempDirectory("kaikki-dumps")
    }

    @AfterTest
    fun cleanup() {
        repo.toFile().deleteRecursively()
        dumps.toFile().deleteRecursively()
    }

    private fun dump(fileLanguage: String, vararg lines: String) {
        dumps.createDirectories()
        dumps.resolve("kaikki.org-dictionary-$fileLanguage.jsonl").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun fixtureLine(name: String) = javaClass.getResource("/kaikki/$name.jsonl")!!.readText().trim()

    private fun words(lang: String) = repo.resolve("backend/src/main/resources/wordpacks/${lang}_guess.txt")
        .readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun writer() = GuessListWriter(repo, dumpDate = "2026-09-14")

    @Test
    fun buildsARussianGuessListFromItsDump() {
        dump("Russian", fixtureLine("ru-noun-forms"), fixtureLine("abbreviation"))

        val reports = WordListBuilder.build("ru", dumps, writer())

        assertEquals(listOf("ru"), reports.map { it.lang })
        val guesses = words("ru")
        assertTrue("собак" in guesses, "5-letter genitive plural of собака: $guesses")
        assertTrue(guesses.none { it.length != 5 }, "only 5-letter words: $guesses")
        assertTrue("м" !in guesses)
    }

    @Test
    fun preReformRussianSpellingsAreDropped() {
        // кормъ: its own "dated" alternative-spelling entry, in pre-1918 orthography (final hard sign).
        dump("Russian", fixtureLine("pre-reform"), fixtureLine("ru-noun-forms"))

        WordListBuilder.build("ru", dumps, writer())

        val guesses = words("ru")
        assertTrue("кормъ" !in guesses, "pre-reform spelling: $guesses")
        assertTrue("собак" in guesses, "modern words still built: $guesses")
    }

    @Test
    fun englishWordsWithoutAVowelAreDropped() {
        // Lowercase abbreviation plurals and sound effects slip past the tags; a real word always has a vowel (y counts).
        dump(
            "English",
            """{"word":"bldgs","lang":"English","pos":"noun","senses":[{"glosses":["plural of bldg"],"tags":["form-of","plural"]}]}""",
            """{"word":"brrrm","lang":"English","pos":"intj","senses":[{"glosses":["engine noise"]}]}""",
            """{"word":"crane","lang":"English","pos":"noun","senses":[{"glosses":["bird"]}]}""",
            """{"word":"lynch","lang":"English","pos":"verb","senses":[{"glosses":["to kill"]}]}""",
        )

        WordListBuilder.build("en", dumps, writer())

        assertEquals(listOf("crane", "lynch"), words("en"))
    }

    @Test
    fun oneUzbekDumpBuildsBothScripts() {
        dump(
            "Uzbek",
            """{"word":"kitob","lang":"Uzbek","pos":"noun","senses":[{"glosses":["book"]}]}""",
            """{"word":"tanga","lang":"Uzbek","pos":"noun","senses":[{"glosses":["coin"]}]}""",
        )

        val reports = WordListBuilder.build("uz", dumps, writer())

        assertEquals(listOf("uz-latn", "uz-cyrl"), reports.map { it.lang })
        assertTrue("kitob" in words("uz-latn"))
        assertTrue("tanga" !in words("uz-latn"), "tanga is 4 Latin tiles")
        assertTrue("китоб" in words("uz-cyrl") && "танга" in words("uz-cyrl"))
    }

    @Test
    fun aMissingDumpSaysWhereToDownloadIt() {
        val error = assertFailsWith<IllegalArgumentException> { WordListBuilder.build("kk", dumps, writer()) }
        assertTrue("https://kaikki.org/dictionary/Kazakh/kaikki.org-dictionary-Kazakh.jsonl" in error.message.orEmpty(), error.message)
    }
}
