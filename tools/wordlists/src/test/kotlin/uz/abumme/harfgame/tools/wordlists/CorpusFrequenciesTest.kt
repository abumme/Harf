package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorpusFrequenciesTest {

    private lateinit var dir: Path

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("corpus")
    }

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    /** A FineWeb-2-shaped Parquet file: one document per row in a `text` column. */
    private fun parquet(name: String, vararg documents: String): Path {
        val file = dir.resolve(name)
        val rows = documents.joinToString(",") { "('" + it.replace("'", "''") + "')" }
        DriverManager.getConnection("jdbc:duckdb:").use { connection ->
            connection.createStatement().use {
                it.execute("COPY (SELECT * FROM (VALUES $rows) AS t(text)) TO '${file.toString().replace('\\', '/')}' (FORMAT parquet)")
            }
        }
        return file
    }

    @Test
    fun countsTheWordTokensOfEveryFileKeepingTheirCase() {
        val first = parquet("a.parquet", "Kitob kitob, KITOB! 2026 yil.", "oʻrdak o'rdak")
        val second = parquet("b.parquet", "«kitob» qalam-qog‘oz")
        val tsv = dir.resolve("counts.tsv")

        CorpusFrequencies.count(listOf(first, second), tsv)

        val counts = tsv.readLines().associate { line -> line.split('\t').let { it[0] to it[1].toLong() } }
        assertEquals(
            mapOf("Kitob" to 1L, "kitob" to 2L, "KITOB" to 1L, "yil" to 1L, "oʻrdak" to 1L, "o'rdak" to 1L, "qalam" to 1L, "qog‘oz" to 1L),
            counts,
        )
    }

    @Test
    fun loadingSumsTheSpellingVariantsOfOnePlayableWord() {
        val tsv = dir.resolve("counts.tsv")
        tsv.writeText("Kitob\t2\nkitob\t5\nKITOB\t1\n'kitob\t1\no'rdak\t3\noʻrdak\t4\nkitoblar\t9\n")

        val words = CorpusFrequencies.load(tsv, PlayableWordFilter(LaunchLanguages.uzLatn))

        assertEquals(Attestation(count = 9, lowercaseCount = 6), words["kitob"])
        assertEquals(Attestation(count = 7, lowercaseCount = 7), words["oʻrdak"])
        assertNull(words["kitoblar"], "8 tiles is not a board length")
    }

    @Test
    fun aWordWrittenMostlyWithACapitalIsNotAttested() {
        assertEquals(0.25, Attestation(count = 8, lowercaseCount = 2).lowercaseShare)
        val policy = AttestationPolicy(minCount = 5, minLowercaseShare = 0.5)
        assertEquals(false, policy.attests(Attestation(count = 80, lowercaseCount = 2))) // a name
        assertEquals(false, policy.attests(Attestation(count = 4, lowercaseCount = 4))) // too rare
        assertEquals(true, policy.attests(Attestation(count = 5, lowercaseCount = 3)))
    }
}
