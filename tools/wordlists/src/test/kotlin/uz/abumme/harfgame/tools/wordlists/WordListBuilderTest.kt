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
    fun uzbekPossessivesEndingInDoubleIAreDropped() {
        // Wiktionary's declension table writes the possessive of teri (skin) as terii; Uzbek spells it terisi.
        dump(
            "Uzbek",
            """{"word":"teri","lang":"Uzbek","pos":"noun","senses":[{"glosses":["skin"]}],""" +
                """"forms":[{"form":"terii","tags":["nominative","possessive","singular"]}]}""",
            """{"word":"kitob","lang":"Uzbek","pos":"noun","senses":[{"glosses":["book"]}]}""",
        )

        WordListBuilder.build("uz", dumps, writer())

        assertTrue("terii" !in words("uz-latn"), "broken possessive: ${words("uz-latn")}")
        assertTrue("терии" !in words("uz-cyrl"), "its transliteration: ${words("uz-cyrl")}")
        assertTrue("kitob" in words("uz-latn") && "китоб" in words("uz-cyrl"))
    }

    private fun resourceDir(name: String): Path = Path.of(javaClass.getResource("/$name")!!.toURI())

    private fun copyResources(from: String, to: Path) {
        to.createDirectories()
        resourceDir(from).toFile().listFiles()!!.forEach { it.copyTo(to.resolve(it.name).toFile()) }
    }

    @Test
    fun kazakhAddsCorpusWordsTheHunspellDictionaryAccepts() {
        dump("Kazakh", """{"word":"кітап","lang":"Kazakh","pos":"noun","senses":[{"glosses":["book"]}]}""")
        copyResources("hunspell", dumps.resolve("hunspell-kk"))
        dumps.resolve("hunspell-kk/kk.aff").toFile().renameTo(dumps.resolve("hunspell-kk/kk_KZ.aff").toFile())
        dumps.resolve("hunspell-kk/kk.dic").toFile().renameTo(dumps.resolve("hunspell-kk/kk_KZ.dic").toFile())
        dumps.resolve("fineweb-2").createDirectories()
        dumps.resolve("fineweb-2/kaz_Cyrl.counts.tsv").writeText(
            "ұлдар\t900\nҰлдар\t100\n" + // an inflected form (ұл + дар)
                "Асқар\t950\nасқар\t50\n" + // a name: the dictionary lists it, but it's written with a capital
                "терек\t900\n" + // written, but the dictionary doesn't know it
                "қала\t900\n" // 4 letters
        )

        val reports = WordListBuilder.build("kk", dumps, writer())

        assertEquals(listOf("кітап", "ұлдар"), words("kk"))
        assertEquals(mapOf("Wiktionary" to 1, "hunspell-kk + FineWeb-2" to 1), reports.single().sourceWords)
    }

    @Test
    fun uzbekAddsInflectedLemmasTheCorpusAttestsInBothScripts() {
        dump("Uzbek", """{"word":"kitob","lang":"Uzbek","pos":"noun","senses":[{"glosses":["book"]}]}""")
        copyResources("uzbek-lemmas", dumps.resolve("uzbek-lemmas/csv/CSV_files"))
        dumps.resolve("uzbek-lemmas/csv/CSV_files/Verbs.csv").writeText("kel,kel\nyoz,yoz\n")
        dumps.resolve("fineweb-2").createDirectories()
        dumps.resolve("fineweb-2/uzn_Latn.counts.tsv").writeText(
            "uylar\t900\n" + // uy + plural
                "keldi\t900\n" + // kel + past
                "yozdim\t900\n" + // 6 Latin tiles, 5 Cyrillic: ёздим
                "uyxon\t900\n" + // written, but no lemma generates it
                "abgor\t3\n" // a lemma, too rare to trust
        )

        WordListBuilder.build("uz", dumps, writer())

        val latin = words("uz-latn")
        val cyrillic = words("uz-cyrl")
        assertTrue(listOf("kitob", "uylar", "keldi").all { it in latin }, "$latin")
        assertTrue(listOf("yozdim", "uyxon", "abgor", "uyning").none { it in latin }, "$latin")
        assertTrue(listOf("китоб", "уйлар", "келди", "ёздим").all { it in cyrillic }, "$cyrillic")
        assertTrue("абгор" !in cyrillic, "$cyrillic")
    }

    @Test
    fun aMissingDumpSaysWhereToDownloadIt() {
        val error = assertFailsWith<IllegalArgumentException> { WordListBuilder.build("kk", dumps, writer()) }
        assertTrue("https://kaikki.org/dictionary/Kazakh/kaikki.org-dictionary-Kazakh.jsonl" in error.message.orEmpty(), error.message)
    }
}
