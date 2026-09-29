package uz.abumme.harfgame.engine

import harf_game.sharedui.generated.resources.Res
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordPackTest {

    private val registry = LanguageRegistry()
    private val repo = WordPackRepository(registry)
    private val languages = listOf("uz-latn", "uz-cyrl", "ru", "en", "kk")

    @Test
    fun packs_load_offline_and_validate_guesses() = runTest {
        val en = repo.load("en")
        assertTrue(en.answers.isNotEmpty(), "answers loaded from resources")

        val tok = registry.tokenizer("en")!!
        assertTrue(en.isValidGuess(tok.tokenize("apple")!!), "known word accepted")
        assertTrue(!en.isValidGuess(tok.tokenize("zzzzz")!!), "non-word rejected")
    }

    @Test
    fun every_pack_passes_integrity_validation() = runTest {
        for (id in languages) {
            val errors = repo.validate(id)
            assertEquals(emptyList(), errors, "pack '$id' integrity")
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun guess_dictionaries_list_each_word_once_in_its_canonical_spelling() = runTest {
        for (id in languages) {
            val tok = registry.tokenizer(id)!!
            val lines = Res.readBytes("files/${id}_guess.txt").decodeToString().lines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val notCanonical = lines.filter { tok.tokenize(it)?.joinToString("") != it }
            assertEquals(emptyList(), notCanonical.take(20), "$id guesses not in canonical spelling")
            val duplicates = lines.groupingBy { tok.tokenize(it) }.eachCount().filterValues { it > 1 }.keys
            assertEquals(emptyList(), duplicates.take(20).map { it?.joinToString("") }, "$id duplicate guesses")
        }
    }

    @Test
    fun uzbek_daily_words_are_always_valid_guesses() = runTest {
        // Uzbek daily answers come from UzbekDailyWords, not the answers file — every one must be
        // submittable, else that day's puzzle would be unwinnable.
        for (id in listOf("uz-latn", "uz-cyrl")) {
            val pack = repo.load(id)
            for (lex in UzbekDailyWords.lexemes) {
                val g = lex.graphemes(id) ?: error("no $id decomposition for ${lex.id}")
                assertTrue(pack.isValidGuess(g), "$id daily '${lex.id}' must be a valid guess")
            }
        }
    }

    @Test
    fun end_to_end_round_per_language() = runTest {
        for (id in languages) {
            val tok = registry.tokenizer(id)!!
            val pack = repo.load(id)
            val answer = pack.answers.first()

            // a losing guess (another answer, if different length skip): pick a same-length guess
            val wrong = pack.guesses.firstOrNull { it != answer && it.size == answer.size }
            if (wrong != null) {
                val res = Scorer.score(wrong, answer)
                assertTrue(res is ScoreResult.Scored, "$id scores a valid guess")
            }
            // winning guess = the answer itself
            val win = Scorer.score(answer, answer)
            assertTrue(win is ScoreResult.Scored && win.marks.all { it == Mark.CORRECT }, "$id winning guess all-correct")
        }
    }
}
