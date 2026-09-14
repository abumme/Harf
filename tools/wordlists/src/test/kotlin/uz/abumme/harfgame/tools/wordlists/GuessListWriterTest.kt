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
import kotlin.test.assertTrue

class GuessListWriterTest {

    private lateinit var repo: Path

    @BeforeTest
    fun setup() {
        repo = Files.createTempDirectory("harf-repo")
    }

    @AfterTest
    fun cleanup() {
        repo.toFile().deleteRecursively()
    }

    private fun backend(name: String) = repo.resolve("backend/src/main/resources/wordpacks/$name")
    private fun bundled(name: String) = repo.resolve("sharedUI/src/commonMain/composeResources/files/$name")
    private fun suggestionBlocklist(name: String) = repo.resolve("backend/src/main/resources/blocklists/$name")

    private fun put(path: Path, text: String) {
        path.parent.createDirectories()
        path.writeText(text)
    }

    private fun words(path: Path) = path.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun writer() = GuessListWriter(repo, dumpDate = "2026-09-14")

    @Test
    fun keepsPreviousWordsAndAnswersDropsBlocklistedWordsAndWritesBothCopies() {
        put(backend("en_guess.txt"), "# old header\napple\ncrane\n")
        put(bundled("en_guess.txt"), "# old header\napple\ncrane\n")
        put(backend("en_answers.txt"), "apple\nplumb\n") // plumb is an answer missing from the old guess file
        put(bundled("en_block.txt"), "shite\n")
        put(suggestionBlocklist("en_block.txt"), "Bitch\n")

        val report = writer().write("en", setOf("zebra", "crane", "shite", "bitch", "quilt"))

        assertEquals(listOf("apple", "crane", "plumb", "quilt", "zebra"), words(backend("en_guess.txt")))
        assertEquals(backend("en_guess.txt").readText(), bundled("en_guess.txt").readText())
        // ["apple","crane","plumb","quilt","zebra"] is 41 bytes of JSON.
        assertEquals(GuessListReport(lang = "en", words = 5, added = 3, packJsonBytes = 41), report)
    }

    @Test
    fun headerNamesTheSourceDumpDateAndLicense() {
        put(backend("ru_guess.txt"), "книга\n")
        put(bundled("ru_guess.txt"), "книга\n")

        writer().write("ru", setOf("книги"))

        val header = backend("ru_guess.txt").readText().lines().takeWhile { it.startsWith("#") }.joinToString("\n")
        for (part in listOf("Wiktionary", "kaikki.org", "2026-09-14", "CC BY-SA")) {
            assertTrue(part in header, "missing '$part' in:\n$header")
        }
    }

    @Test
    fun aPreviousWordOutsideTodaysFiltersIsStillKept() {
        put(backend("en_guess.txt"), "zzz\n")
        put(bundled("en_guess.txt"), "zzz\n")

        writer().write("en", setOf("apple"))

        assertEquals(listOf("apple", "zzz"), words(backend("en_guess.txt")))
    }

    @Test
    fun uzbekDailyWordsAreAlwaysGuessesInTheirOwnScript() {
        writer().write("uz-latn", emptySet())
        writer().write("uz-cyrl", emptySet())

        assertTrue("shahar" in words(backend("uz-latn_guess.txt")))
        assertTrue("шаҳар" in words(bundled("uz-cyrl_guess.txt")))
    }
}
