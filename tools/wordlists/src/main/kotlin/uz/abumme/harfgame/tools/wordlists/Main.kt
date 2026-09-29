package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess

private val USAGE = """
    Builds per-language guess dictionaries from kaikki.org (Wiktextract) JSONL dumps.

    Usage: ./gradlew :tools:wordlists:run --args="--dump-date <yyyy-mm-dd> [options]"

      --dumps <dir>        directory holding kaikki.org-dictionary-<Language>.jsonl
                           (default: tools/wordlists/cache)
      --dump-date <date>   day the dumps were downloaded; written into every file header
      --languages <list>   comma-separated en, ru, kk, uz (uz builds uz-latn and uz-cyrl); default: all
      --help               show this help

    Rewrites <lang>_guess.txt in backend/src/main/resources/wordpacks/ and
    sharedUI/src/commonMain/composeResources/files/ (paths relative to the repository root).
""".trimIndent()

fun main(args: Array<String>) {
    if (args.isEmpty() || "--help" in args || "-h" in args) {
        println(USAGE)
        return
    }
    val options = parseOptions(args) ?: fail("$USAGE", status = 2)
    val dumpDate = options["--dump-date"] ?: fail("--dump-date is required\n\n$USAGE", status = 2)
    try {
        LocalDate.parse(dumpDate)
    } catch (e: DateTimeParseException) {
        fail("--dump-date must be yyyy-mm-dd, got '$dumpDate'", status = 2)
    }
    val dumps = Path.of(options["--dumps"] ?: "tools/wordlists/cache")
    val languages = options["--languages"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: WordListBuilder.dumpLanguages.keys.toList()

    val writer = GuessListWriter(repoRoot = Path.of("").toAbsolutePath(), dumpDate = dumpDate)
    for (language in languages) {
        val reports = try {
            WordListBuilder.build(language, dumps, writer)
        } catch (e: IllegalArgumentException) {
            fail(e.message.orEmpty(), status = 1)
        }
        for (report in reports) {
            println("%-8s %7d words (+%d new), guesses JSON %d bytes".format(report.lang, report.words, report.added, report.packJsonBytes))
            report.sourceWords.forEach { (source, count) -> println("%-8s %7d from %s".format("", count, source)) }
        }
    }
}

/** `--name value` pairs, or null when an argument is unknown or lacks a value. */
private fun parseOptions(args: Array<String>): Map<String, String>? {
    val known = setOf("--dumps", "--dump-date", "--languages")
    val options = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i]
        val value = args.getOrNull(i + 1)
        if (name !in known || value == null) {
            System.err.println("Unexpected argument: $name")
            return null
        }
        options[name] = value
        i += 2
    }
    return options
}

private fun fail(message: String, status: Int): Nothing {
    System.err.println(message)
    exitProcess(status)
}
