package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.useLines

/** How often a word occurs in a corpus, and how often in lowercase: names are nearly always written with a capital. */
data class Attestation(val count: Long, val lowercaseCount: Long) {
    val lowercaseShare: Double get() = if (count == 0L) 0.0 else lowercaseCount.toDouble() / count

    operator fun plus(other: Attestation) = Attestation(count + other.count, lowercaseCount + other.lowercaseCount)
}

/** When corpus usage vouches for a dictionary word: seen often enough, and mostly in lowercase. */
data class AttestationPolicy(val minCount: Long, val minLowercaseShare: Double) {
    fun attests(attestation: Attestation?): Boolean =
        attestation != null && attestation.count >= minCount && attestation.lowercaseShare >= minLowercaseShare
}

/**
 * Word counts from a text corpus in Parquet files with a `text` column (FineWeb-2's layout). Counting runs once per
 * corpus into a `token<TAB>count` file that keeps each token's case, so loading can tell names from ordinary words.
 */
object CorpusFrequencies {
    /** Letters, combining marks and every apostrophe Uzbek writers use for oʻ/gʻ; anything else separates words. */
    private const val SEPARATOR = """[^\p{L}\p{M}'’‘`´ʻʼʹ′]+"""
    private val apostrophes = setOf('\'', '’', '‘', '`', '´', 'ʻ', 'ʼ', 'ʹ', '′')

    /** Counts tokens of [tokenLengths] characters: a window around the board lengths keeps the grouping small. */
    fun count(parquetFiles: List<Path>, out: Path, tokenLengths: IntRange = 2..24) {
        require(parquetFiles.isNotEmpty()) { "no corpus files to count" }
        val files = parquetFiles.joinToString(",", "[", "]") { "'" + sqlPath(it) + "'" }
        out.parent?.createDirectories()
        DriverManager.getConnection("jdbc:duckdb:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET preserve_insertion_order = false")
                // Gigabytes of text: cap memory and let the grouping spill to disk instead of starving the machine. Each
                // thread holds a decompressed Parquet row group of long documents, so fewer threads is what fits.
                statement.execute("SET memory_limit = '6GB'")
                statement.execute("SET threads = 4")
                statement.execute("SET temp_directory = '${sqlPath(out.toAbsolutePath().parent.resolve("duckdb-tmp"))}'")
                val query = """
                    SELECT token, count(*) AS n FROM (
                        SELECT unnest(regexp_split_to_array(text, '${SEPARATOR.replace("'", "''")}')) AS token
                        FROM read_parquet($files)
                    )
                    WHERE length(token) BETWEEN ${tokenLengths.first} AND ${tokenLengths.last}
                    GROUP BY token
                """.trimIndent()
                statement.executeQuery(query).use { rows ->
                    out.bufferedWriter().use { writer ->
                        while (rows.next()) {
                            writer.append(rows.getString(1)).append('\t').append(rows.getLong(2).toString()).append('\n')
                        }
                    }
                }
            }
        }
    }

    /**
     * The corpus words [filter] can play, keyed by canonical spelling with the counts of all their variants summed
     * (case, apostrophe style, a quotation mark stuck to the word).
     */
    fun load(counts: Path, filter: PlayableWordFilter): Map<String, Attestation> {
        val words = HashMap<String, Attestation>()
        counts.useLines { lines ->
            for (line in lines) {
                val tab = line.lastIndexOf('\t')
                if (tab <= 0) continue
                val token = line.substring(0, tab)
                val count = line.substring(tab + 1).toLongOrNull() ?: continue
                val word = filter.playable(token) ?: filter.playable(token.trimStart { it in apostrophes }) ?: continue
                val lowercase = if (token == token.lowercase()) count else 0L
                words.merge(word, Attestation(count, lowercase), Attestation::plus)
            }
        }
        return words
    }

    private fun sqlPath(path: Path): String = path.toAbsolutePath().toString().replace('\\', '/').replace("'", "''")
}
