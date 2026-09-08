package uz.abumme.harfgame.engine

import harf_game.sharedui.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords

/**
 * Tokenized vocabulary for a language: curated answers + the full guess set (includes answers) plus
 * the daily [schedule] (tokenized, indexed from [anchorEpochDay]). Defaults let call sites that only
 * need answers/guesses (e.g. tests) build a pack without a schedule.
 */
data class WordPack(
    val languageId: String,
    val answers: List<List<String>>,
    val guesses: Set<List<String>>,
    val schedule: List<List<String>> = emptyList(),
    val anchorEpochDay: Long = 0L,
) {
    fun isValidGuess(graphemes: List<String>): Boolean = graphemes in guesses
}

/**
 * Resolves the active word pack as the freshest valid one: a locally-cached server pack when present
 * and valid, otherwise the bundled pack — both offline. The bundled schedule is generated with the
 * shared [WordPackSchedule] so it matches the server's seeded version 1.
 */
@OptIn(ExperimentalResourceApi::class)
class WordPackRepository(
    private val registry: LanguageRegistry,
    private val cache: WordPackCache? = null,
) {

    private val cached = HashMap<String, WordPack>()
    private val mutex = Mutex()

    suspend fun load(id: String): WordPack {
        cached[id]?.let { return it }
        return mutex.withLock {
            cached[id]?.let { return@withLock it } // re-check inside the lock
            val tokenizer = registry.tokenizer(id) ?: error("Unknown language: $id")

            val fromServer = cache?.get(id)?.let { buildFromDto(id, it, tokenizer) }
            (fromServer ?: buildBundled(id, tokenizer)).also { cached[id] = it }
        }
    }

    /** Force a re-resolve on next load (e.g. after a new pack is cached). */
    suspend fun invalidate(id: String) = mutex.withLock { cached.remove(id) }

    /** True if a fetched pack passes integrity and can replace the current one. */
    fun isAdoptable(dto: WordPackDto): Boolean {
        val tokenizer = registry.tokenizer(dto.lang) ?: return false
        return buildFromDto(dto.lang, dto, tokenizer) != null
    }

    private fun buildFromDto(id: String, dto: WordPackDto, tokenizer: Tokenizer): WordPack? {
        val config = registry.config(id) ?: return null
        val answers = dto.answers.map { tokenizer.tokenize(it) ?: return null }
        val schedule = dto.schedule.map { tokenizer.tokenize(it) ?: return null }
        if (answers.isEmpty() || schedule.isEmpty()) return null
        // Integrity: answers have a supported length and appear in the guess set.
        val guesses = dto.guesses.mapNotNull { tokenizer.tokenize(it) }.toMutableSet()
        guesses += answers
        guesses += schedule
        for (a in answers) if (a.size !in config.minLength..config.maxLength || a !in guesses) return null
        return WordPack(id, answers, guesses, schedule, dto.anchorEpochDay)
    }

    private suspend fun buildBundled(id: String, tokenizer: Tokenizer): WordPack {
        val answers = readLines("files/${id}_answers.txt").mapNotNull { tokenizer.tokenize(it) }
        val guessesRaw = readLines("files/${id}_guess.txt").mapNotNull { tokenizer.tokenize(it) }
        val isUz = id == "uz-latn" || id == "uz-cyrl"

        // Uzbek daily answers come from UzbekDailyWords (paired lexemes), not the answers file.
        val dailyAnswers = if (isUz) UzbekDailyWords.lexemes.mapNotNull { it.graphemes(id) } else emptyList()

        val schedule: List<List<String>> = if (isUz) {
            val order = WordPackSchedule.buildOrder(UzbekDailyWords.lexemes.size, WordPackSchedule.seedFor("uz"))
            order.mapNotNull { UzbekDailyWords.lexemes[it].graphemes(id) }
        } else {
            val rawAnswers = readLines("files/${id}_answers.txt")
            WordPackSchedule.build(rawAnswers, WordPackSchedule.seedFor(id)).mapNotNull { tokenizer.tokenize(it) }
        }

        val guesses = (guessesRaw + answers + dailyAnswers + schedule).toSet()
        return WordPack(id, answers, guesses, schedule, WordPackSchedule.ANCHOR_EPOCH_DAY)
    }

    /**
     * Integrity check for the BUNDLED files: every entry tokenizes, every answer has a supported
     * length, and every answer appears in the guess dictionary. Returns problems (empty = ok).
     */
    suspend fun validate(id: String): List<String> {
        val config = registry.config(id) ?: return listOf("Unknown language: $id")
        val tokenizer = Tokenizer(config)
        val errors = ArrayList<String>()

        // Offensive-word blocklist (files/<id>_block.txt): reviewers list words that must never
        // ship as an answer or guess. Kept per language; the check fails the build if any slips in.
        val blocked = readLines("files/${id}_block.txt").map { it.lowercase() }.toSet()
        fun flagIfBlocked(line: String) {
            if (line.lowercase() in blocked) errors.add("blocked (offensive) word: '$line'")
        }

        val guessTokens = HashSet<List<String>>()
        for (line in readLines("files/${id}_guess.txt")) {
            flagIfBlocked(line)
            val t = tokenizer.tokenize(line)
            if (t == null) errors.add("guess not tokenizable: '$line'") else guessTokens.add(t)
        }
        for (line in readLines("files/${id}_answers.txt")) {
            flagIfBlocked(line)
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
