package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real lines come from kaikki.org dumps downloaded 2026-09-14; synthetic lines mirror the same structure. */
class KaikkiSelectionTest {

    private fun fixture(name: String): KaikkiEntry =
        KaikkiReader.parse(javaClass.getResource("/kaikki/$name.jsonl")!!.readText().trim())!!

    private fun entry(json: String): KaikkiEntry = KaikkiReader.parse(json)!!

    private fun senses(vararg tags: String) =
        tags.joinToString(",", "[", "]") { t -> if (t.isEmpty()) """{"glosses":["x"]}""" else """{"glosses":["x"],"tags":["$t"]}""" }

    @Test
    fun nounContributesItsWordAndInflectedFormsButNotRomanizations() {
        val candidates = EntryFilter.candidates(fixture("ru-noun-forms")) // собака
        assertTrue("собака" in candidates, "headword")
        assertTrue("соба́ки" in candidates, "stress-marked inflected form, normalized later")
        assertTrue("соба́к" in candidates, "genitive plural")
        assertTrue("sobáka" !in candidates, "romanization is not a word")
    }

    @Test
    fun tableAndTemplateRowsAreNotWords() {
        val noun = entry(
            """{"word":"стол","lang":"Russian","lang_code":"ru","pos":"noun","senses":[{"glosses":["table"]}],""" +
                """"forms":[{"form":"ru-noun-table","tags":["table-tags"]},{"form":"ru-decl-noun","tags":["inflection-template"]},""" +
                """{"form":"2","tags":["class"]},{"form":"столы́","tags":["nominative","plural"]}]}"""
        )
        assertEquals(listOf("стол", "столы́"), EntryFilter.candidates(noun))
    }

    @Test
    fun ordinaryWordWithADerogatorySenseIsKept() {
        // собака has a figurative derogatory sense next to its ordinary ones.
        assertTrue(EntryFilter.candidates(fixture("ru-noun-forms")).isNotEmpty())
    }

    @Test
    fun ordinaryWordWithAVulgarSenseIsKept() {
        // Kazakh көт: an ordinary sense plus a vulgar one.
        assertTrue("көт" in EntryFilter.candidates(fixture("one-vulgar-sense")))
    }

    @Test
    fun wordWhoseEverySenseIsRudeIsDropped() {
        for (tag in listOf("vulgar", "offensive", "derogatory", "slur")) {
            val rude = entry("""{"word":"xxxxx","lang":"English","pos":"noun","senses":${senses(tag, tag)}}""")
            assertEquals(emptyList(), EntryFilter.candidates(rude), tag)
        }
    }

    @Test
    fun rudeTagOnTheEntryItselfDropsTheWord() {
        val rude = entry("""{"word":"xxxxx","lang":"English","pos":"noun","tags":["vulgar"],"senses":${senses("", "")}}""")
        assertEquals(emptyList(), EntryFilter.candidates(rude))
    }

    @Test
    fun capitalizedEntryIsNotAnOrdinaryWord() {
        // English MTOCs: its own plural "form-of" entry, untagged, although its parent MTOC is an initialism.
        assertEquals(emptyList(), EntryFilter.candidates(fixture("acronym-plural")))
    }

    @Test
    fun onlyLowercaseSpellingsOfAnEntryAreCandidates() {
        val polish = entry(
            """{"word":"polish","lang":"English","pos":"noun","senses":[{"glosses":["a shine"]}],""" +
                """"forms":[{"form":"Polish","tags":["capitalized"]},{"form":"polishes","tags":["plural"]}]}"""
        )
        assertEquals(listOf("polish", "polishes"), EntryFilter.candidates(polish))
    }

    @Test
    fun properNounEntryIsDropped() {
        assertEquals(emptyList(), EntryFilter.candidates(fixture("name"))) // Kazakh Израиль, pos "name"
    }

    @Test
    fun abbreviationIsDropped() {
        assertEquals(emptyList(), EntryFilter.candidates(fixture("abbreviation"))) // Russian м
    }

    @Test
    fun ordinaryWordWithAnAbbreviationSenseIsKept() {
        // Russian общий: ordinary senses plus a slang clipping of общежитие.
        assertTrue("общий" in EntryFilter.candidates(fixture("one-abbreviation-sense")))
        for (tag in listOf("abbreviation", "acronym", "initialism")) {
            val mixed = entry("""{"word":"xxxxx","lang":"English","pos":"noun","senses":${senses("", tag)}}""")
            assertEquals(listOf("xxxxx"), EntryFilter.candidates(mixed), tag)
        }
    }

    @Test
    fun wordWhoseEverySenseIsAnAbbreviationIsDropped() {
        for (tag in listOf("abbreviation", "acronym", "initialism")) {
            val abbreviation = entry("""{"word":"xxxxx","lang":"English","pos":"noun","senses":${senses(tag, tag)}}""")
            assertEquals(emptyList(), EntryFilter.candidates(abbreviation), tag)
        }
    }

    @Test
    fun abbreviationTagOnTheEntryItselfDropsTheWord() {
        val abbreviation = entry("""{"word":"xxxxx","lang":"English","pos":"noun","tags":["initialism"],"senses":${senses("", "")}}""")
        assertEquals(emptyList(), EntryFilter.candidates(abbreviation))
    }

    @Test
    fun aMisspellingSenseDropsTheWordEvenBesideOrdinarySenses() {
        // English theif: an obsolete spelling of thief, and a misspelling of it.
        val mixed = entry("""{"word":"xxxxx","lang":"English","pos":"noun","senses":${senses("", "misspelling")}}""")
        assertEquals(emptyList(), EntryFilter.candidates(mixed))
    }

    @Test
    fun nonWordPartsOfSpeechAreDropped() {
        for (pos in listOf("name", "character", "prefix", "suffix", "infix", "interfix", "phrase", "proverb", "symbol", "punct", "abbrev", "romanization", "combining_form")) {
            val nonWord = entry("""{"word":"xxxxx","lang":"English","pos":"$pos","senses":${senses("")}}""")
            assertEquals(emptyList(), EntryFilter.candidates(nonWord), pos)
        }
    }

    @Test
    fun unreadableLinesAreSkipped() {
        assertNull(KaikkiReader.parse(""))
        assertNull(KaikkiReader.parse("""{"word":"truncated","pos":"no"""))
        assertNull(KaikkiReader.parse("""{"pos":"noun","senses":[]}""")) // no headword
    }

    @Test
    fun readerStreamsEveryReadableEntryOfADump() {
        val dump = Files.createTempFile("kaikki", ".jsonl")
        try {
            dump.writeText(
                """{"word":"apple","lang":"English","pos":"noun","senses":[{"glosses":["fruit"]}]}""" + "\n" +
                    """{"word":"broken""" + "\n" +
                    """{"word":"crane","lang":"English","pos":"noun","senses":[{"glosses":["bird"]}]}""" + "\n"
            )
            assertEquals(listOf("apple", "crane"), KaikkiReader.useEntries(dump) { entries -> entries.map { it.word }.toList() })
        } finally {
            Files.deleteIfExists(dump)
        }
    }
}
