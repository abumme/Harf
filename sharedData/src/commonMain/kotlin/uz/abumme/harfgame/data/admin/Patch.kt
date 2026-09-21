package uz.abumme.harfgame.data.admin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * One field of a PATCH body that tells "leave unchanged" (key absent) apart from "set", including an explicit JSON
 * `null` that clears a nullable field. Declare it with a [Patch.Unchanged] default and
 * `@EncodeDefault(EncodeDefault.Mode.NEVER)` so an unchanged field is left out of the JSON.
 */
@Serializable(with = PatchSerializer::class)
sealed interface Patch<out T> {
    data object Unchanged : Patch<Nothing>

    data class Set<T>(val value: T) : Patch<T>
}

/** The value to store: the new one when set, [current] when unchanged. */
fun <T> Patch<T>.valueOr(current: T): T = when (this) {
    Patch.Unchanged -> current
    is Patch.Set -> value
}

class PatchSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<Patch<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Patch<T>) = when (value) {
        // Reached only when a Patch property lacks @EncodeDefault(NEVER): JSON has no value meaning "absent".
        Patch.Unchanged -> throw IllegalStateException("An unchanged Patch field must be left out of the JSON")
        is Patch.Set -> encoder.encodeSerializableValue(valueSerializer, value.value)
    }

    // Called only when the key is present; an absent key keeps the property's Unchanged default.
    override fun deserialize(decoder: Decoder): Patch<T> = Patch.Set(decoder.decodeSerializableValue(valueSerializer))
}
