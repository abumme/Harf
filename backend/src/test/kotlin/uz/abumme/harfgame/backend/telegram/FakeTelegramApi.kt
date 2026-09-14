package uz.abumme.harfgame.backend.telegram

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Records every Bot API call and answers it with [respond]; a null answer means Telegram did not confirm.
 * By default it confirms like Telegram does: `true` for answerCallbackQuery, a Message for sends and edits.
 */
internal class FakeTelegramApi(
    var respond: (method: String, body: JsonObject) -> JsonElement? = { method, _ ->
        if (method == "answerCallbackQuery") JsonPrimitive(true) else buildJsonObject { put("message_id", 1) }
    },
) : TelegramApi {
    val calls = mutableListOf<Pair<String, JsonObject>>()

    override suspend fun call(method: String, body: JsonObject): JsonElement? {
        calls += method to body
        return respond(method, body)
    }

    fun sent(method: String): List<JsonObject> = calls.filter { it.first == method }.map { it.second }
}
