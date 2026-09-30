package org.kotlogramme.cli.adapter.format

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
 * is the shared projection, so every format carries the same fields.
 */
internal val UPDATE_HEADERS = listOf("kind", "chat", "message_id", "from", "time", "text")

/** Prints one update as the configured output format. */
fun Output.renderUpdate(update: IncomingUpdate) {
    table(UPDATE_HEADERS, listOf(updateRow(update)))
}

/** Prints one update as a single JSON object, for one-object-per-line output. */
fun Output.renderUpdateJson(update: IncomingUpdate) {
    line(updateJson(update))
}

/** The row for one [update]; pure so it can be snapshot-tested without an [Output]. */
internal fun updateRow(update: IncomingUpdate): List<String> = when (update) {
    is IncomingUpdate.NewMessage -> listOf(
        NEW_MESSAGE_KIND,
        update.chat?.title.orEmpty(),
        update.message.id.toString(),
        update.message.senderName,
        DateTimeFormatter.ISO_INSTANT.format(update.message.sentAt),
        update.message.text,
    )
    is IncomingUpdate.Other -> listOf(update.kind, "", "", "", "", "")
}

/** [update] as one JSON object keyed by [UPDATE_HEADERS]; pure, for tests and JSON Lines. */
internal fun updateJson(update: IncomingUpdate): String {
    val row = updateRow(update)
    return JsonObject(
        UPDATE_HEADERS.withIndex().associate { (column, header) -> header to JsonPrimitive(row[column]) },
    ).toString()
}

private const val NEW_MESSAGE_KIND = "message"
