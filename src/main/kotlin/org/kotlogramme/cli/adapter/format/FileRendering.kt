package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Message
import java.time.format.DateTimeFormatter

/**
 * Renders a chat's file listing.
 *
 * The row is plain data so the same projection feeds every [Output] format. [size] is the exact
 * byte count rather than the human `1.6 MB` the message view shows: a listing is the place a caller
 * looks before moving bytes around, and the plain and JSON formats then carry a number it can add
 * up instead of a string it has to parse. A detail the media does not have is left empty, as the
 * `pinned` column of the dialog list is.
 */
internal val FILE_HEADERS = listOf("id", "kind", "size", "name", "duration", "date")

/** Prints the files as the configured output format. */
fun Output.renderFiles(messages: List<Message>) {
    table(FILE_HEADERS, messages.map(::fileRow))
}

/** The row for one [message]; pure so it can be snapshot-tested without an [Output]. */
internal fun fileRow(message: Message): List<String> = listOf(
    message.id.toString(),
    message.media?.kind.orEmpty(),
    message.media?.sizeBytes?.toString().orEmpty(),
    message.media?.name.orEmpty(),
    message.media?.durationSeconds?.let(::formatDuration).orEmpty(),
    DateTimeFormatter.ISO_INSTANT.format(message.sentAt),
)
