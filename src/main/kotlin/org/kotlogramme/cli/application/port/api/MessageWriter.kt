package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Message

/**
 * The writing side of the message surface: sending, editing, deleting, forwarding, pinning,
 * reacting, read receipts and the chat action a conversation shows.
 *
 * One cohesive port rather than seven: every method is a small, independent mutation of the same
 * conversation, and the CLI exposes them as sibling commands.
 */
interface MessageWriter {
    fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message

    fun edit(reference: String, messageId: Int, text: String): Message

    fun delete(reference: String, messageIds: List<Int>): Int

    fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message>

    fun pin(reference: String, messageId: Int)

    fun unpin(reference: String, messageId: Int)

    /** Removes every pinned message from the chat, which is `messagesUnpinAllMessages`. */
    fun unpinAll(reference: String)

    /** The chat's pinned message, or `null` when it has none. */
    fun pinnedMessage(reference: String): Message?

    fun react(reference: String, messageId: Int, emoji: String)

    fun removeReaction(reference: String, messageId: Int)

    fun markRead(reference: String)

    /** Reports [activity] in [reference], which is what the conversation shows beside it. */
    fun sendChatAction(reference: String, activity: ChatActivity)
}
