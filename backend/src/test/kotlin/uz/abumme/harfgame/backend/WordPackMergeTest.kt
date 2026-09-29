package uz.abumme.harfgame.backend

import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.backend.service.GuessMerge
import uz.abumme.harfgame.backend.service.WordPackServerService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordPackMergeTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    /** A service whose bundled word-pack resources are exactly [files] (file name → lines). */
    private fun serviceWith(vararg files: Pair<String, List<String>>): WordPackServerService {
        val resources = files.toMap()
        return WordPackServerService(resourceLines = { name -> resources[name].orEmpty() })
    }

    private suspend fun pack(lang: String) = WordPackServerService().getPack(lang)

    @Test
    fun missingWordsAreAddedWithOneVersionAdvance() = runBlocking {
        insertPack("en", "3", listOf("apple", "crane"))

        val added = serviceWith("en_guess.txt" to listOf("apple", "crane", "quilt", "zebra")).mergeGuesses()

        assertEquals(mapOf("en" to GuessMerge(added = 2)), added)
        assertEquals(setOf("apple", "crane", "quilt", "zebra"), pack("en")!!.guesses.toSet())
        assertEquals("4", pack("en")!!.version)
    }

    @Test
    fun aRestartWithNothingNewKeepsTheVersion() = runBlocking {
        insertPack("en", "3", listOf("apple"))
        val service = serviceWith("en_guess.txt" to listOf("apple", "quilt"))

        service.mergeGuesses()
        assertEquals(emptyMap(), service.mergeGuesses())
        assertEquals("4", pack("en")!!.version)
    }

    @Test
    fun storedWordsMissingFromTheResourcesAreKept() = runBlocking {
        insertPack("ru", "7", listOf("книга", "бырка")) // бырка: accepted by an editor after the build

        serviceWith("ru_guess.txt" to listOf("книга", "книги")).mergeGuesses()

        assertEquals(setOf("книга", "бырка", "книги"), pack("ru")!!.guesses.toSet())
    }

    @Test
    fun answersScheduleAndEffectiveDateAreUntouched() = runBlocking {
        insertPack(
            "en", "1", listOf("apple", "crane"),
            answers = listOf("apple", "crane"), schedule = listOf("crane", "apple"), effectiveFrom = 20_454L,
        )

        serviceWith("en_guess.txt" to listOf("zebra")).mergeGuesses()

        val merged = pack("en")!!
        assertEquals(listOf("apple", "crane"), merged.answers)
        assertEquals(listOf("crane", "apple"), merged.schedule)
        assertEquals(20_454L, merged.effectiveFrom)
    }

    @Test
    fun answersFileWordsReachTheGuessesToo() = runBlocking {
        insertPack("en", "1", listOf("apple"))

        assertEquals(mapOf("en" to GuessMerge(added = 1)), serviceWith("en_answers.txt" to listOf("plumb")).mergeGuesses())
        assertEquals(setOf("apple", "plumb"), pack("en")!!.guesses.toSet())
    }

    @Test
    fun wordsDifferingOnlyInCaseOrSpacingAreNotDuplicated() = runBlocking {
        insertPack("en", "2", listOf("Apple"))

        assertEquals(emptyMap(), serviceWith("en_guess.txt" to listOf("apple", " APPLE ", "", "# header")).mergeGuesses())
        assertEquals(listOf("Apple"), pack("en")!!.guesses)
        assertEquals("2", pack("en")!!.version)
    }

    @Test
    fun aSpellingVariantOfAStoredWordIsNotAdded() = runBlocking {
        insertPack("ru", "4", listOf("актёр")) // ё plays as е, so актер is the same word
        insertPack("uz-latn", "2", listOf("o'rdak")) // any apostrophe plays as the tutuq

        val service = serviceWith("ru_guess.txt" to listOf("актер"), "uz-latn_guess.txt" to listOf("oʻrdak"))

        assertEquals(emptyMap(), service.mergeGuesses())
        assertEquals(listOf("актёр"), pack("ru")!!.guesses)
        assertEquals("4", pack("ru")!!.version)
    }

    @Test
    fun storedSpellingVariantsCollapseToTheDeployedSpelling() = runBlocking {
        // A first-pass list seeded актёр, then a dictionary merge added актер: one word stored twice.
        insertPack("ru", "4", listOf("актёр", "книга", "актер", "полёт", "полет"))

        val merged = serviceWith("ru_guess.txt" to listOf("актер", "книги")).mergeGuesses()

        assertEquals(mapOf("ru" to GuessMerge(added = 1, variantsRemoved = 2)), merged)
        assertEquals(listOf("книга", "актер", "полёт", "книги"), pack("ru")!!.guesses)
        assertEquals("5", pack("ru")!!.version)
    }

    @Test
    fun anAcceptedSuggestionSpelledLikeAStoredWordIsNotAdded() = runBlocking {
        insertPack("ru", "4", listOf("актер"))

        assertEquals(false, WordPackServerService().addGuess("ru", "Актёр"))
        assertEquals(listOf("актер"), pack("ru")!!.guesses)
    }

    @Test
    fun languagesWithoutAStoredPackAreLeftToSeeding() = runBlocking {
        assertEquals(emptyMap(), serviceWith("kk_guess.txt" to listOf("кітап")).mergeGuesses())
        assertNull(pack("kk"))
    }
}
