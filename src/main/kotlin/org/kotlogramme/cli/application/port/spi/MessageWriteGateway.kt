package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Message

/** The message-writing operations the facade exposes, in domain terms. */
interface MessageWriteGateway {
    fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message

    fun edit(reference: String, messageId: Int, text: String): Message

    /** Returns how many messages were deleted. */
    fun delete(reference: String, messageIds: List<Int>): Int

    fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message>

    fun pin(reference: String, messageId: Int)

    fun unpin(reference: String, messageId: Int)

    fun react(reference: String, messageId: Int, emoji: String)

    fun removeReaction(reference: String, messageId: Int)

    fun markRead(reference: String)

    fun sendChatAction(reference: String, activity: ChatActivity)
}
