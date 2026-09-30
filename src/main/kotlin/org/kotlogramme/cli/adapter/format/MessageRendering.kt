package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Message
import java.time.format.DateTimeFormatter

/**
 * Renders a page of messages.
 *
 * The row is plain data so every [Output] format carries the same fields: the reply marker and the
 * media placeholder are their own columns, not concatenated into the text.
 */
internal val MESSAGE_HEADERS = listOf("id", "time", "from", "reply", "media", "text")

/** Prints the messages as the configured output format. */
fun Output.renderMessages(messages: List<Message>) {
    table(MESSAGE_HEADERS, messages.map(::messageRow))
}

/** The row for one [message]; pure so it can be snapshot-tested without an [Output]. */
internal fun messageRow(message: Message): List<String> = listOf(
    message.id.toString(),
    DateTimeFormatter.ISO_INSTANT.format(message.sentAt),
    message.senderName,
    message.replyToMessageId?.toString().orEmpty(),
    message.mediaKind?.let { "[$it]" }.orEmpty(),
    message.text,
)
