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
        if (guesses.any { it.equals(normalized, ignoreCase = true) }) return@dbQuery false
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
     * `<lang>_guess.txt` or `<lang>_answers.txt` that its stored pack lacks (compared case-insensitively, as
     * [addGuess] does) is appended, and the version advances once. Words are never removed and answers,
     * schedule and effectiveFrom are never touched, so words accepted after the build survive and past days stay
     * unchanged. Languages without a stored pack are left to [seed]. Returns how many words each changed language gained.
     */
    suspend fun mergeGuesses(): Map<String, Int> {
        val added = LinkedHashMap<String, Int>()
        for (lang in languages()) {
            val bundled = readLines("${lang}_guess.txt") + readLines("${lang}_answers.txt")
            if (bundled.isEmpty()) continue
            DatabaseFactory.dbQuery {
                val row = WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.single()
                val guesses: List<String> = json.decodeFromString(row[WordPacksTable.guesses])
                val known = guesses.mapTo(HashSet()) { it.lowercase() }
                val missing = bundled.filter { known.add(it.lowercase()) }
                if (missing.isNotEmpty()) {
                    WordPacksTable.update({ WordPacksTable.lang eq lang }) {
                        it[WordPacksTable.guesses] = json.encodeToString(guesses + missing)
                        it[WordPacksTable.version] = nextVersion(row[WordPacksTable.version])
                        it[WordPacksTable.updatedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
                    }
                    added[lang] = missing.size
                }
            }
        }
        return added
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

/** Raw lines of a bundled word-pack resource (`resources/wordpacks/<name>`); empty when the file is absent. */
private fun bundledWordPackLines(name: String): List<String> {
    val stream = WordPackServerService::class.java.getResourceAsStream("/wordpacks/$name") ?: return emptyList()
    return stream.bufferedReader().useLines { it.toList() }
}
