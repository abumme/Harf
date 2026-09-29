package uz.abumme.harfgame.backend.service

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule

/**
 * Serves and seeds versioned word packs. Version 1 is seeded from maintained vocab files in
 * `resources/wordpacks/`, with a deterministic no-adjacent-repeat schedule from the shared
 * [WordPackSchedule] generator — so a fresh offline client (which runs the same generator on the
 * bundled vocab) agrees with a client synced to version 1. Future versions only append to the
 * schedule, keeping past days immutable.
 */
class WordPackServerService(
    private val anchorDay: Long = WordPackSchedule.ANCHOR_EPOCH_DAY,
    /** Raw lines of a bundled word-pack file by name; the resources on the classpath by default. */
    private val resourceLines: (name: String) -> List<String> = ::bundledWordPackLines,
) {
    private val json = Json

    suspend fun getPack(lang: String): WordPackDto? = DatabaseFactory.dbQuery {
        WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.singleOrNull()?.let { row ->
            WordPackDto(
                lang = row[WordPacksTable.lang],
                version = row[WordPacksTable.version],
                effectiveFrom = row[WordPacksTable.effectiveFrom],
                anchorEpochDay = row[WordPacksTable.anchorEpochDay],
                answers = json.decodeFromString(row[WordPacksTable.answers]),
                guesses = json.decodeFromString(row[WordPacksTable.guesses]),
                schedule = json.decodeFromString(row[WordPacksTable.schedule]),
            )
        }
    }

    /** Languages that have a word pack, alphabetically. */
    suspend fun languages(): List<String> = DatabaseFactory.dbQuery {
        WordPacksTable.select(WordPacksTable.lang).orderBy(WordPacksTable.lang).map { it[WordPacksTable.lang] }
    }

    /**
     * Append an accepted [word] to [lang]'s guesses and bump the pack version so clients pick it up
     * on their next pull. Answers/schedule/effectiveFrom are untouched, keeping past days immutable.
     * Idempotent per word: a word already present only bumps nothing. Returns true if the pack changed.
     */
    suspend fun addGuess(lang: String, word: String): Boolean = DatabaseFactory.dbQuery {
        val row = WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.singleOrNull()
            ?: return@dbQuery false
        val guesses: List<String> = json.decodeFromString(row[WordPacksTable.guesses])
        val normalized = word.trim()
        val key = GuessSpelling.key(lang, normalized)
        if (guesses.any { GuessSpelling.key(lang, it) == key }) return@dbQuery false
        val newGuesses = guesses + normalized
        val newVersion = nextVersion(row[WordPacksTable.version])
        WordPacksTable.update({ WordPacksTable.lang eq lang }) {
            it[WordPacksTable.guesses] = json.encodeToString(newGuesses)
            it[WordPacksTable.version] = newVersion
            it[WordPacksTable.updatedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        }
        true
    }

    /**
     * Append-only merge of the bundled guess dictionaries into the stored packs. Every word of a language's
     * `<lang>_guess.txt` or `<lang>_answers.txt` that its stored pack lacks is appended, and the version advances once.
     * Words are compared by [GuessSpelling.key], as [addGuess] does, so a spelling variant is the word it spells:
     * variants are never added, and variants already stored twice collapse to one spelling (the deployed one when it is
     * among them). No word is otherwise removed, and answers, schedule and effectiveFrom are never touched, so words
     * accepted after the build survive and past days stay unchanged. Languages without a stored pack are left to [seed].
     * Returns what changed per changed language.
     */
    suspend fun mergeGuesses(): Map<String, GuessMerge> {
        val merges = LinkedHashMap<String, GuessMerge>()
        for (lang in languages()) {
            val bundled = readLines("${lang}_guess.txt") + readLines("${lang}_answers.txt")
            if (bundled.isEmpty()) continue
            DatabaseFactory.dbQuery {
                val row = WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.single()
                val stored: List<String> = json.decodeFromString(row[WordPacksTable.guesses])
                val guesses = collapseVariants(lang, stored, bundled)
                val known = guesses.mapTo(HashSet()) { GuessSpelling.key(lang, it) }
                val missing = bundled.filter { known.add(GuessSpelling.key(lang, it)) }
                val merge = GuessMerge(added = missing.size, variantsRemoved = stored.size - guesses.size)
                if (merge.added > 0 || merge.variantsRemoved > 0) {
                    WordPacksTable.update({ WordPacksTable.lang eq lang }) {
                        it[WordPacksTable.guesses] = json.encodeToString(guesses + missing)
                        it[WordPacksTable.version] = nextVersion(row[WordPacksTable.version])
                        it[WordPacksTable.updatedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
                    }
                    merges[lang] = merge
                }
            }
        }
        return merges
    }

    /** [stored] with one spelling per word, in stored order: the [bundled] spelling when stored, else the first stored. */
    private fun collapseVariants(lang: String, stored: List<String>, bundled: List<String>): List<String> {
        val bundledSpellings = bundled.toHashSet()
        val chosen = stored.groupBy { GuessSpelling.key(lang, it) }
            .mapValues { (_, spellings) -> spellings.firstOrNull { it in bundledSpellings } ?: spellings.first() }
        return stored.filter { chosen[GuessSpelling.key(lang, it)] == it }.distinct()
    }

    /** Insert version 1 for every language if absent. Idempotent: existing rows are left untouched. */
    suspend fun seed() {
        for (lang in listOf("en", "ru", "kk")) {
            val answers = readLines("${lang}_answers.txt")
            if (answers.isEmpty()) continue
            val schedule = WordPackSchedule.build(answers, WordPackSchedule.seedFor(lang))
            insertIfAbsent(lang, answers, readLines("${lang}_guess.txt"), schedule)
        }

        // Uzbek: one lexeme order materialized into both scripts so a day is the same lexeme in each.
        val pairs = readLines("uz_lexemes.tsv").map { it.split('\t') }.filter { it.size == 2 }
        if (pairs.isNotEmpty()) {
            val order = WordPackSchedule.buildOrder(pairs.size, WordPackSchedule.seedFor("uz"))
            insertIfAbsent("uz-latn", pairs.map { it[0] }, readLines("uz-latn_guess.txt"), order.map { pairs[it][0] })
            insertIfAbsent("uz-cyrl", pairs.map { it[1] }, readLines("uz-cyrl_guess.txt"), order.map { pairs[it][1] })
        }
    }

    private suspend fun insertIfAbsent(lang: String, answers: List<String>, guessesRaw: List<String>, schedule: List<String>) {
        DatabaseFactory.dbQuery {
            if (WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.any()) return@dbQuery
            val guesses = (guessesRaw + answers).distinct() // answers are always submittable guesses
            WordPacksTable.insert {
                it[WordPacksTable.lang] = lang
                it[WordPacksTable.version] = "1"
                it[WordPacksTable.effectiveFrom] = anchorDay
                it[WordPacksTable.anchorEpochDay] = anchorDay
                it[WordPacksTable.answers] = json.encodeToString(answers)
                it[WordPacksTable.guesses] = json.encodeToString(guesses)
                it[WordPacksTable.schedule] = json.encodeToString(schedule)
                it[WordPacksTable.updatedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            }
        }
    }

    private fun readLines(name: String): List<String> =
        resourceLines(name).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun nextVersion(current: String): String = (current.toIntOrNull()?.plus(1) ?: 2).toString()
}

/** What merging the deployed dictionaries changed in one language's pack. */
data class GuessMerge(val added: Int, val variantsRemoved: Int = 0)

/**
 * The identity of a guess word regardless of how it is spelled, matching the app's normalization in LaunchLanguages:
 * case and surrounding spaces never matter, Russian ё plays as е, and any apostrophe in Uzbek Latin is the tutuq (ʻ).
 */
object GuessSpelling {
    private val apostrophes = setOf('\'', '’', '‘', '`', '´', 'ʼ', 'ʹ', '′')

    fun key(lang: String, word: String): String {
        val lower = word.trim().lowercase()
        return when (lang) {
            "ru" -> lower.replace('ё', 'е')
            "uz-latn" -> lower.map { if (it in apostrophes) 'ʻ' else it }.joinToString("")
            else -> lower
        }
    }
}

/** Raw lines of a bundled word-pack resource (`resources/wordpacks/<name>`); empty when the file is absent. */
private fun bundledWordPackLines(name: String): List<String> {
    val stream = WordPackServerService::class.java.getResourceAsStream("/wordpacks/$name") ?: return emptyList()
    return stream.bufferedReader().useLines { it.toList() }
}
