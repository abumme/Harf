package uz.abumme.harfgame.tools.wordlists

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.bufferedReader

/** One Wiktextract dictionary entry, reduced to what word selection needs. */
data class KaikkiEntry(
    val word: String,
    val pos: String?,
    val tags: Set<String>,
    /** Tags of each sense, in order; a sense without tags is an empty set. */
    val senseTags: List<Set<String>>,
    val forms: List<KaikkiForm>,
)

data class KaikkiForm(val form: String, val tags: Set<String>)

/** Reads kaikki.org JSONL dumps: one JSON object per line, streamed so multi-GB dumps need little memory. */
object KaikkiReader {
    private val json = Json { ignoreUnknownKeys = true }

    /** The entry on [line], or null for a blank, truncated or headword-less line. */
    fun parse(line: String): KaikkiEntry? {
        if (line.isBlank()) return null
        val root = try {
            json.parseToJsonElement(line) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return null
        return KaikkiEntry(
            word = root.string("word") ?: return null,
            pos = root.string("pos"),
            tags = root.tags(),
            senseTags = root.array("senses").mapNotNull { (it as? JsonObject)?.tags() },
            forms = root.array("forms").mapNotNull { element ->
                val form = element as? JsonObject ?: return@mapNotNull null
                form.string("form")?.let { KaikkiForm(it, form.tags()) }
            },
        )
    }

    /** Streams the readable entries of [dump] to [block]; the file is open only while [block] runs. */
    fun <T> useEntries(dump: Path, block: (Sequence<KaikkiEntry>) -> T): T =
        dump.bufferedReader().use { reader -> block(reader.lineSequence().mapNotNull(::parse)) }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.array(key: String): List<Any> = (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.tags(): Set<String> =
        array("tags").mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
}
