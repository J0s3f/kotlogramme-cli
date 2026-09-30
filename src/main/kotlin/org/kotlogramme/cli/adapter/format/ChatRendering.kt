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
    chat.lastMessagePreview.orEmpty(),
)
