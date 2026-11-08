package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kotlogramme.cli.application.port.spi.Output

/**
 * An [Output] that keeps what a command would print as one JSON object instead of printing it.
 *
 * Tools reuse the renderers the command line uses, so a tool and the matching command report the same
 * fields: a table becomes `items`, a continuation cursor `next_cursor`, and the statements their usual keys.
 * Everything a renderer prints lands in the object; nothing is dropped.
 */
class CapturedOutput : Output {
    private var items: MutableList<JsonObject>? = null
    private val fields = linkedMapOf<String, JsonElement>()

    override fun line(text: String) {
        fields["message"] = JsonPrimitive(text)
    }

    override fun table(headers: List<String>, rows: List<List<String>>, title: String?) {
        val captured = items ?: mutableListOf<JsonObject>().also { items = it }
        rows.forEach { row ->
            captured += JsonObject(
                headers.withIndex().associate { (column, header) ->
                    header to JsonPrimitive(row.getOrElse(column) { "" })
                },
            )
        }
    }

    override fun message(text: String) = line(text)

    override fun nextPage(cursor: String) {
        fields["next_cursor"] = JsonPrimitive(cursor)
    }

    override fun count(total: Int) {
        fields["count"] = JsonPrimitive(total)
    }

    override fun record(fields: List<Pair<String, String>>) {
        fields.forEach { (key, value) -> this.fields[key] = JsonPrimitive(value) }
    }

    override fun details(headline: String, fields: List<Pair<String, String>>) {
        line(headline)
        record(fields)
    }

    override fun properties(properties: List<Pair<String, String>>) {
        properties.forEach { (label, value) -> fields[label.lowercase().replace(' ', '_')] = JsonPrimitive(value) }
    }

    fun result(): JsonObject = JsonObject(
        buildMap {
            items?.let { put("items", JsonArray(it)) }
            putAll(fields)
        },
    )
}
