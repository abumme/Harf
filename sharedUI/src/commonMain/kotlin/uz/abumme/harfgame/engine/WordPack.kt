package uz.abumme.harfgame.engine

import harf_game.sharedui.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords

/** Tokenized vocabulary for a language: curated answers + the full guess set (includes answers). */
data class WordPack(
    val languageId: String,
    val answers: List<List<String>>,
    val guesses: Set<List<String>>,
) {
    fun isValidGuess(graphemes: List<String>): Boolean = graphemes in guesses
}

/**
 * Loads bundled word packs from app resources (offline) and caches them. Guess membership
 * is O(1) via a Set of tokenized forms; answers are always part of the guess set.
 */
@OptIn(ExperimentalResourceApi::class)
class WordPackRepository(private val registry: LanguageRegistry) {

    private val cache = HashMap<String, WordPack>()
    private val mutex = Mutex()

    suspend fun load(id: String): WordPack {
        cache[id]?.let { return it }
        return mutex.withLock {
            cache[id]?.let { return@withLock it } // re-check inside the lock
            val tokenizer = registry.tokenizer(id) ?: error("Unknown language: $id")

            val answers = readLines("files/${id}_answers.txt").mapNotNull { tokenizer.tokenize(it) }
            val guessesRaw = readLines("files/${id}_guess.txt").mapNotNull { tokenizer.tokenize(it) }
            // Uzbek daily answers come from UzbekDailyWords, not the answers file — fold them in
            // so every daily word is always a submittable guess (see DailyPuzzleProvider).
            val dailyAnswers = if (id == "uz-latn" || id == "uz-cyrl") {
                UzbekDailyWords.lexemes.mapNotNull { it.graphemes(id) }
            } else {
                emptyList()
            }
            val guesses = (guessesRaw + answers + dailyAnswers).toSet()

            WordPack(id, answers, guesses).also { cache[id] = it }
        }
    }

    /**
     * Integrity check: every entry tokenizes, every answer has a supported length, and
     * every answer appears in the guess dictionary. Returns a list of problems (empty = ok).
     */
    suspend fun validate(id: String): List<String> {
        val config = registry.config(id) ?: return listOf("Unknown language: $id")
        val tokenizer = Tokenizer(config)
        val errors = ArrayList<String>()

        val guessTokens = HashSet<List<String>>()
        for (line in readLines("files/${id}_guess.txt")) {
            val t = tokenizer.tokenize(line)
            if (t == null) errors.add("guess not tokenizable: '$line'") else guessTokens.add(t)
        }
        for (line in readLines("files/${id}_answers.txt")) {
            val t = tokenizer.tokenize(line)
            if (t == null) {
                errors.add("answer not tokenizable: '$line'")
                continue
            }
            if (t.size !in config.minLength..config.maxLength) {
                errors.add("answer length ${t.size} out of ${config.minLength}..${config.maxLength}: '$line'")
            }
            if (t !in guessTokens) errors.add("answer not in guess dictionary: '$line'")
        }
        return errors
    }

    private suspend fun readLines(path: String): List<String> =
        Res.readBytes(path).decodeToString()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
}
