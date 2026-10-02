package org.kotlogramme.cli.application

import org.kotlogramme.cli.domain.Chat

/** Where a dialogs listing continues from, as the facade's offset triple. */
internal data class DialogsCursor(val peerId: Long, val topMessageId: Int, val dateMillis: Long)

/**
 * The opaque cursor a listing command prints as `# next:` and reads back as `--after`.
 *
 * The format is per listing shape, and the command that prints it and the gateway that parses it
 * agree on it here:
 * - message-id listings (`history`, `search`, `list-files`, `chat-photo-history`) use the last row's
 *   message id, which the facade takes as `offsetId`.
 * - offset-index listings (`blocked`, `profile-photos`, `members`) use the next index - the previous
 *   offset plus the rows returned - which the facade takes as `offset`.
 * - `dialogs` uses `<peerId>:<topMessageId>:<epochMillis>` from the last row, which the facade takes
 *   as `offsetPeer`/`offsetId`/`offsetDate`.
 */
internal object ListingCursor {
    /**
     * Parses a message-id or offset-index cursor, which is a bare decimal.
     *
     * A cursor that is not a number is a usage error, not a silent first page.
     */
    fun parseDecimal(cursor: String): Int =
        cursor.toIntOrNull() ?: throw IllegalArgumentException("malformed cursor '$cursor'")

    /** Parses a dialogs cursor into the facade's offset triple. */
    fun parseDialogs(cursor: String): DialogsCursor {
        val parts = cursor.split(':')
        if (parts.size != 3) throw IllegalArgumentException("malformed cursor '$cursor'")
        return DialogsCursor(
            peerId = parts[0].toLongOrNull() ?: throw IllegalArgumentException("malformed cursor '$cursor'"),
            topMessageId = parts[1].toIntOrNull() ?: throw IllegalArgumentException("malformed cursor '$cursor'"),
            dateMillis = parts[2].toLongOrNull() ?: throw IllegalArgumentException("malformed cursor '$cursor'"),
        )
    }

    /** The dialogs cursor for the row a page ended on. */
    fun formatDialogs(chat: Chat): String =
        "${chat.id}:${chat.topMessageId}:${chat.lastMessageAt?.toEpochMilli() ?: 0L}"
}
