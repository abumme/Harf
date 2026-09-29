package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.readText

/**
 * The pipeline for one language: read entries → select words → keep playable spellings → write the pack files.
 * Wiktionary (a kaikki.org dump) is always a source. Kazakh and Uzbek add larger dictionaries when their files are in
 * the dump directory, but only for words FineWeb-2 shows people actually write (design.md, "Additional sources").
 */
object WordListBuilder {

    /** Dump language → kaikki.org language name. `uz` builds both Uzbek scripts from one dump. */
    val dumpLanguages: Map<String, String> = linkedMapOf("en" to "English", "ru" to "Russian", "kk" to "Kazakh", "uz" to "Uzbek")

    /**
     * How much FineWeb-2 usage vouches for a dictionary word. A name is written with a capital almost every time, an
     * ordinary word mostly in lowercase; a handful of occurrences in billions of words is more often a typo than a word.
     */
    val attestation: Map<String, AttestationPolicy> = mapOf(
        "kk" to AttestationPolicy(minCount = 5, minLowercaseShare = 0.5),
        "uz" to AttestationPolicy(minCount = 5, minLowercaseShare = 0.5),
    )

    private const val WIKTIONARY = "Wiktionary"
    private const val KAZAKH_HUNSPELL = "hunspell-kk + FineWeb-2"
    private const val UZBEK_LEMMAS = "UzbekLemmaStems + FineWeb-2"

    fun build(dumpLanguage: String, dumpsDir: Path, writer: GuessListWriter): List<GuessListReport> {
        val name = requireNotNull(dumpLanguages[dumpLanguage]) {
            "unknown dump language '$dumpLanguage'; expected one of ${dumpLanguages.keys}"
        }
        val dump = dumpsDir.resolve("kaikki.org-dictionary-$name.jsonl")
        require(dump.exists()) { "missing dump $dump: download https://kaikki.org/dictionary/$name/kaikki.org-dictionary-$name.jsonl" }

        if (dumpLanguage == "uz") return buildUzbek(dump, dumpsDir, writer)

        val filter = PlayableWordFilter(LaunchLanguages.all.getValue(dumpLanguage))
        // Streams: only playable words are held in memory, so multi-GB dumps stay small.
        val wiktionary = KaikkiReader.useEntries(dump) { entries ->
            entries.flatMap { EntryFilter.candidates(it) }
                .mapNotNull { filter.playable(it) }
                .filterTo(HashSet()) { looksLikeAWord(dumpLanguage, it) }
        }
        val hunspell = if (dumpLanguage == "kk") kazakhHunspellWords(dumpsDir) else null
        val sources = linkedMapOf(WIKTIONARY to wiktionary.size)
        hunspell?.let { sources[KAZAKH_HUNSPELL] = it.size }
        val report = writer.write(
            dumpLanguage,
            wiktionary + hunspell.orEmpty(),
            extraSources = if (hunspell == null) emptyList() else listOf(hunspellCredit(dumpsDir), fineWebCredit("kaz_Cyrl")),
        )
        return listOf(report.copy(sourceWords = sources))
    }

    private fun buildUzbek(dump: Path, dumpsDir: Path, writer: GuessListWriter): List<GuessListReport> {
        // Cyrillic needs the raw spellings: tile counts are recomputed after transliteration.
        val raw = KaikkiReader.useEntries(dump) { entries -> entries.flatMap { EntryFilter.candidates(it) }.toList() }
        val latin = PlayableWordFilter(LaunchLanguages.uzLatn)
        val lemmaForms = uzbekLemmaWords(dumpsDir)
        val credits = if (lemmaForms == null) emptyList() else listOf(uzbekLemmasCredit(dumpsDir), fineWebCredit("uzn_Latn"))

        val latinWiktionary = raw.mapNotNull { latin.playable(it) }.filterTo(HashSet()) { looksLikeAWord("uz-latn", it) }
        val latinLemmas = lemmaForms.orEmpty().mapNotNull { latin.playable(it) }.filterTo(HashSet()) { looksLikeAWord("uz-latn", it) }
        val cyrillicWiktionary = UzbekCyrillic.words(raw).filterTo(HashSet()) { looksLikeAWord("uz-cyrl", it) }
        val cyrillicLemmas = UzbekCyrillic.words(lemmaForms.orEmpty()).filterTo(HashSet()) { looksLikeAWord("uz-cyrl", it) }

        fun sources(wiktionary: Int, lemmas: Int) =
            linkedMapOf(WIKTIONARY to wiktionary).apply { if (lemmaForms != null) put(UZBEK_LEMMAS, lemmas) }
        return listOf(
            writer.write("uz-latn", latinWiktionary + latinLemmas, credits)
                .copy(sourceWords = sources(latinWiktionary.size, latinLemmas.size)),
            writer.write("uz-cyrl", cyrillicWiktionary + cyrillicLemmas, credits)
                .copy(sourceWords = sources(cyrillicWiktionary.size, cyrillicLemmas.size)),
        )
    }

    /** Corpus words the Kazakh Hunspell dictionary spells, or null when its files or the corpus are missing. */
    private fun kazakhHunspellWords(dumpsDir: Path): Set<String>? {
        val aff = dumpsDir.resolve("hunspell-kk/kk_KZ.aff")
        val dic = dumpsDir.resolve("hunspell-kk/kk_KZ.dic")
        if (!aff.exists() || !dic.exists()) return null
        val counts = corpusCounts(dumpsDir, "kaz_Cyrl", tokenLengths = 4..8) ?: return null
        val corpus = CorpusFrequencies.load(counts, PlayableWordFilter(LaunchLanguages.kk))
        val policy = attestation.getValue("kk")
        val hunspell = HunspellDictionary(aff, dic)
        return corpus.filterTo(HashMap()) { (word, usage) -> policy.attests(usage) && hunspell.accepts(word) }.keys
    }

    /**
     * Attested Uzbek Latin forms of the lemma dataset's words, or null when the dataset or the corpus is missing.
     * Forms of 4–6 tiles are kept, not just 5: transliteration changes tile counts (yozdim is ёздим).
     */
    private fun uzbekLemmaWords(dumpsDir: Path): Set<String>? {
        val csv = dumpsDir.resolve("uzbek-lemmas/csv/CSV_files")
        if (!csv.exists()) return null
        val counts = corpusCounts(dumpsDir, "uzn_Latn", tokenLengths = 3..16) ?: return null
        val corpus = CorpusFrequencies.load(counts, PlayableWordFilter(LaunchLanguages.uzLatn, lengths = (4..6).toSet()))
        val policy = attestation.getValue("uz")
        val words = HashSet<String>()
        for (lemma in UzbekLemmas.read(csv)) {
            UzbekInflection.forms(lemma.word, lemma.pos, maxTiles = 6).filterTo(words) { policy.attests(corpus[it]) }
        }
        return words
    }

    /**
     * The word counts of a FineWeb-2 language, counted from its Parquet files the first time; null without either.
     * [tokenLengths] is in characters and covers the board's tiles with room to spare (Uzbek digraphs, stray quotes).
     */
    private fun corpusCounts(dumpsDir: Path, code: String, tokenLengths: IntRange): Path? {
        val dir = dumpsDir.resolve("fineweb-2")
        val counts = dir.resolve("$code.counts.tsv")
        if (counts.exists()) return counts
        val parquet = if (dir.exists()) dir.listDirectoryEntries("$code-*.parquet").sorted() else emptyList()
        if (parquet.isEmpty()) return null
        println("counting the words of ${parquet.size} FineWeb-2 $code file(s); this runs once")
        val partial = dir.resolve("$code.counts.tsv.partial")
        CorpusFrequencies.count(parquet, partial, tokenLengths)
        partial.moveTo(counts, StandardCopyOption.ATOMIC_MOVE)
        return counts
    }

    private fun commit(dir: Path): String =
        dir.resolve("COMMIT").takeIf { it.exists() }?.readText()?.trim()?.take(7)?.let { ", commit $it" }.orEmpty()

    private fun hunspellCredit(dumpsDir: Path) =
        "hunspell-kk (https://github.com/taem/hunspell-kk${commit(dumpsDir.resolve("hunspell-kk"))}), MPL 1.1: " +
            "words its dictionary spells, kept when FineWeb-2 attests them."

    private fun uzbekLemmasCredit(dumpsDir: Path) =
        "UzbekLemmaStems-POS-Dataset (https://github.com/MaksudSharipov/UzbekLemmaStems-POS-Dataset" +
            "${commit(dumpsDir.resolve("uzbek-lemmas"))}), Apache-2.0: lemmas inflected by tools/wordlists, kept when FineWeb-2 attests them."

    private fun fineWebCredit(code: String) =
        "FineWeb-2 (https://huggingface.co/datasets/HuggingFaceFW/fineweb-2, $code), ODC-By 1.0: word counts only."

    /**
     * Language-specific spellings the dictionary tags don't catch: pre-1918 Russian orthography ends words in a hard
     * sign (кормъ), English abbreviation plurals and sound effects have no vowel (bldgs, brrrm; y counts, as in lynch),
     * and Wiktionary's Uzbek declension tables give nouns ending in -i a possessive in -ii (terii for terisi).
     */
    private fun looksLikeAWord(lang: String, word: String): Boolean = when (lang) {
        "ru" -> !word.endsWith("ъ")
        "en" -> word.any { it in "aeiouy" }
        "uz-latn" -> !word.endsWith("ii")
        "uz-cyrl" -> !word.endsWith("ии")
        else -> true
    }
}
