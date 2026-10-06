package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kotlogramme.raw.RawTelegramApi
import org.kotlogramme.raw.RawValue
import java.util.HexFormat

/**
 * Decodes a raw update's TL payload to JSON text, keyed by the field names of the bundled schema.
 *
 * Telegram sends these updates as a bare `Update` constructor, which the facade's decoder only reads
 * as part of a method result. The payload is therefore wrapped in an `updateShort` and read as the
 * result of a method that returns `Updates`; the wrapper is dropped again. A payload the schema
 * cannot decode is kept as `{"undecoded":"<hex>"}`, so the data is never lost.
 */
internal fun RawUpdate.toJson(): String = decoded().toString()

/** The payload as a JSON element, decoded once so a caller that needs its text and a field does not decode twice. */
internal fun RawUpdate.decoded(): JsonElement =
    runCatching { decodeUpdate(data) }.getOrNull() ?: JsonObject(mapOf("undecoded" to JsonPrimitive(hex(data))))

/** The `user_id` field of a decoded payload, or `null` when it has none. */
internal fun JsonElement.userId(): Long? =
    (this as? JsonObject)?.get("user_id")?.let { (it as? JsonPrimitive)?.longOrNull }

/** The `user_id` field of the payload, or `null` when it has none or cannot be decoded. */
internal fun RawUpdate.userId(): Long? = decoded().userId()

/** Decodes [payload], an `Update` in TL form, into JSON; throws when the schema cannot read it. */
internal fun decodeUpdate(payload: ByteArray): JsonElement {
    val wrapped = UPDATE_SHORT_ID + payload + UPDATE_SHORT_DATE
    val updates = RawTelegramApi.decodeResponse(UPDATES_METHOD, wrapped) as RawValue.Object
    val update = updates.fields.getValue("update") as RawValue.Object
    return update.fields.mapValues { (_, value) -> value.toJson() }.let(::JsonObject)
}

private fun RawValue.toJson(): JsonElement = when (this) {
    is RawValue.Object -> {
        val named = mapOf("constructor" to JsonPrimitive(constructorName))
        JsonObject(named + fields.mapValues { it.value.toJson() })
    }
    is RawValue.VectorValue -> JsonArray(values.map { it.toJson() })
    is RawValue.BooleanValue -> JsonPrimitive(value)
    is RawValue.IntValue -> JsonPrimitive(value)
    is RawValue.LongValue -> JsonPrimitive(value)
    is RawValue.DoubleValue -> JsonPrimitive(value)
    is RawValue.StringValue -> JsonPrimitive(value)
    is RawValue.BytesValue -> JsonPrimitive(hex(value))
}

private fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)

/** `updateShort#78d4dec1 update:Update date:int = Updates`, little-endian. */
private val UPDATE_SHORT_ID = byteArrayOf(0xc1.toByte(), 0xde.toByte(), 0xd4.toByte(), 0x78)
private val UPDATE_SHORT_DATE = ByteArray(Int.SIZE_BYTES)

/** Any schema method whose result type is `Updates`; only its result type is used, nothing is sent. */
private const val UPDATES_METHOD = "account.getNotifyExceptions"
