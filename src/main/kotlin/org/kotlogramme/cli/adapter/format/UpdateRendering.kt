package org.kotlogramme.cli.adapter.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.IncomingUpdate
import java.time.format.DateTimeFormatter

/**
 * Renders an incoming update.
 *
 * A live update is one event, so it is projected to a single row: the table and plain formats show
 * that row, and [renderUpdateJson] emits one JSON object per line for JSON Lines scripting. The row
 * is the shared projection, so every format carries the same fields. Names are for reading; the
 * `chat_id` and `sender_id` columns are what a script passes back to address the chat or the person,
 * because names are neither unique nor stable.
 */
internal val UPDATE_HEADERS =
    listOf("kind", "chat", "message_id", "from", "time", "text", "chat_id", "sender_id")

/** Prints one update as the configured output format. */
fun Output.renderUpdate(update: IncomingUpdate, styler: MessageStyler = MessageStyler.PLAIN) {
    table(UPDATE_HEADERS, listOf(updateRow(update, styler)))
}

/** Prints one update as a single JSON object, for one-object-per-line output. */
fun Output.renderUpdateJson(update: IncomingUpdate) {
    line(updateJson(update))
}

/** The row for one [update]; pure so it can be snapshot-tested without an [Output]. */
internal fun updateRow(
    update: IncomingUpdate,
    styler: MessageStyler = MessageStyler.PLAIN,
): List<String> = when (update) {
    is IncomingUpdate.NewMessage -> listOf(
        NEW_MESSAGE_KIND,
        update.chat?.title.orEmpty(),
        update.message.id.toString(),
        update.message.senderName,
        DateTimeFormatter.ISO_INSTANT.format(update.message.sentAt),
        styler.style(update.message.text, update.message.entities),
        update.chat?.id?.toString().orEmpty(),
        update.message.senderId?.toString().orEmpty(),
    )
    is IncomingUpdate.Other -> listOf(update.kind, "", "", "", "", update.data, "", update.userId?.toString().orEmpty())
}

/** [update] as one JSON object keyed by [UPDATE_HEADERS]; pure, for tests and JSON Lines. */
internal fun updateJson(update: IncomingUpdate): String {
    val row = updateRow(update)
    val columns = UPDATE_HEADERS.withIndex().associate { (column, header) -> header to JsonPrimitive(row[column]) }
    return JsonObject(columns + dataColumn(update)).toString()
}

/**
 * An update's data is JSON already, so it is nested as an object instead of an escaped string, and
 * the text column that carries it in a table is left empty.
 */
private fun dataColumn(update: IncomingUpdate): Map<String, JsonElement> = when (update) {
    is IncomingUpdate.Other -> if (update.data.isEmpty()) {
        emptyMap()
    } else {
        mapOf("text" to JsonPrimitive(""), "data" to Json.parseToJsonElement(update.data))
    }
    is IncomingUpdate.NewMessage -> emptyMap()
}

private const val NEW_MESSAGE_KIND = "message"
