package uz.abumme.harfgame.tools.wordlists

import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.wordpack.CalendarSnapshotDto
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackIntegrity
import uz.abumme.harfgame.lang.LaunchLanguages
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Takes a snapshot of every launch language's published calendar for a release build: `<lang>_calendar.json` (answers
 * and schedule of `GET /api/v1/wordpacks/<lang>`) next to the bundled guess dictionaries. Nothing is written unless all
 * five packs pass the app's own [WordPackIntegrity] check combined with the bundled guesses, and the two Uzbek schedules
 * describe the same days.
 */
object CalendarSnapshotRefresh {
    private val json = Json { ignoreUnknownKeys = true }
    private val output = Json { prettyPrint = false }

    val languages: List<String> = listOf("en", "ru", "kk", "uz-latn", "uz-cyrl")

    /** The snapshot file contents per language from [fetch] (a pack's JSON) and [guesses] (the bundled guess lines). */
    fun snapshots(fetch: (lang: String) -> String, guesses: (lang: String) -> List<String>): Map<String, String> {
        val packs = languages.associateWith { lang ->
            val raw = try {
                fetch(lang)
            } catch (e: Exception) {
                throw IllegalStateException("$lang: could not fetch the pack (${e.javaClass.simpleName}: ${e.message})", e)
            }
            val pack = try {
                json.decodeFromString<WordPackDto>(raw)
            } catch (e: Exception) {
                throw IllegalStateException("$lang: not a word pack (${e.message})", e)
            }
            check(pack.lang == lang) { "$lang: the server answered with the ${pack.lang} pack" }
            val snapshot = CalendarSnapshotDto.of(pack)
            val config = LaunchLanguages.all[lang] ?: error("$lang: no language rules")
            val check = WordPackIntegrity.check(snapshot.toPack(guesses(lang)), config)
            if (check is WordPackIntegrity.Invalid) error("$lang: the app would refuse this snapshot: ${check.problems.take(5)}")
            snapshot
        }
        val latn = packs.getValue("uz-latn")
        val cyrl = packs.getValue("uz-cyrl")
        check(latn.anchorEpochDay == cyrl.anchorEpochDay && latn.schedule.size == cyrl.schedule.size) {
            "uz-latn and uz-cyrl schedules differ in length or anchor; refresh again"
        }
        return packs.mapValues { (_, snapshot) -> output.encodeToString(snapshot) + "\n" }
    }

    /** The published pack of [lang] at [baseUrl] (e.g. `https://api.lazydevs.uz/harf`). */
    fun fetchFrom(baseUrl: String): (String) -> String {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
        return { lang ->
            val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + ApiRoutes.wordpack(lang)))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) { "$lang: HTTP ${response.statusCode()} from ${request.uri()}" }
            response.body()
        }
    }

    /** Guess lines of a bundled `<lang>_guess.txt` in [dir], as the app reads them. */
    fun bundledGuesses(dir: Path): (String) -> List<String> = { lang ->
        Files.readAllLines(dir.resolve("${lang}_guess.txt")).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }
}

/**
 * `refreshCalendarSnapshot <baseUrl> <outputDir> <guessDir>`: run by `./gradlew :sharedUI:refreshCalendarSnapshot`, which
 * passes `harf.apiBaseUrl` and the app's Compose resources directory.
 */
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Usage: refreshCalendarSnapshot <baseUrl> <outputDir> <guessDir>")
        exitProcess(2)
    }
    val (baseUrl, outputDir, guessDir) = args
    val files = try {
        CalendarSnapshotRefresh.snapshots(CalendarSnapshotRefresh.fetchFrom(baseUrl), CalendarSnapshotRefresh.bundledGuesses(Path.of(guessDir)))
    } catch (e: Exception) {
        System.err.println("Calendar snapshot not refreshed: ${e.message}")
        exitProcess(1)
    }
    val out = Files.createDirectories(Path.of(outputDir))
    for ((lang, content) in files) {
        val file = out.resolve(CalendarSnapshotDto.resourcePath(lang).substringAfterLast('/'))
        Files.writeString(file, content)
        println("%-8s %s".format(lang, file))
    }
}
