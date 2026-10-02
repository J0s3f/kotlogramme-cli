package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Chat

/**
 * Renders a dialog list.
 *
 * The rows are plain data so the same projection feeds every [Output] format; the unread count and
 * the pinned flag become explicit columns rather than decoration.
 */
internal val CHAT_HEADERS = listOf("id", "kind", "title", "unread", "pinned", "last")

/** Prints the dialogs as the configured output format. */
fun Output.renderChats(chats: List<Chat>) {
    table(CHAT_HEADERS, chats.map(::chatRow))
}

/** The row for one [chat]; pure so it can be snapshot-tested without an [Output]. */
internal fun chatRow(chat: Chat): List<String> = listOf(
    chat.id.toString(),
    chat.kind.name.lowercase(),
    chat.title,
    chat.unreadCount.toString(),
    if (chat.pinned) "yes" else "",
    previewOf(chat.lastMessagePreview),
)

/** Longest preview the dialog table shows before it is cut. */
internal const val PREVIEW_LIMIT = 60

/**
 * Collapses [preview] to a single truncated line.
 *
 * A chat's last message can be an essay: rendered verbatim it pads the table to the width of the
 * longest message, which is what a live run showed. Newlines would break the rows outright. The cut
 * is by display columns rather than code units so an emoji or an ideograph does not leave the cell
 * narrower than the column it was measured for.
 */
internal fun previewOf(preview: String?): String =
    truncateToWidth(preview.orEmpty().replace(WHITESPACE_RUN, " ").trim(), PREVIEW_LIMIT)

private val WHITESPACE_RUN = Regex("\\s+")
