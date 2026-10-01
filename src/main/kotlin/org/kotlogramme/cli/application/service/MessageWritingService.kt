package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.spi.MessageWriteGateway
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Message

/**
 * Writes to a conversation through the [MessageWriteGateway].
 *
 * Malformed input is rejected here: a blank text, an empty id list and a non-positive message id
 * never reach the gateway, so the failure is a clear [IllegalArgumentException] instead of a
 * Telegram error.
 */
class MessageWritingService(private val gateway: MessageWriteGateway) : MessageWriter {
    override fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message {
        requireNotBlank(text)
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.sendText(reference, text, replyToMessageId, silent)
    }

    override fun edit(reference: String, messageId: Int, text: String): Message {
        requireNotBlank(text)
        requirePositiveMessageId(messageId)
        return gateway.edit(reference, messageId, text)
    }

    override fun delete(reference: String, messageIds: List<Int>): Int {
        requireMessageIds(messageIds)
        return gateway.delete(reference, messageIds)
    }

    override fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message> {
        requireMessageIds(messageIds)
        return gateway.forward(fromReference, messageIds, toReference)
    }

    override fun pin(reference: String, messageId: Int) {
        requirePositiveMessageId(messageId)
        gateway.pin(reference, messageId)
    }

    override fun unpin(reference: String, messageId: Int) {
        requirePositiveMessageId(messageId)
        gateway.unpin(reference, messageId)
    }

    override fun unpinAll(reference: String) = gateway.unpinAll(reference)

    override fun pinnedMessage(reference: String): Message? = gateway.pinnedMessage(reference)

    override fun react(reference: String, messageId: Int, emoji: String) {
        requirePositiveMessageId(messageId)
        gateway.react(reference, messageId, emoji)
    }

    override fun removeReaction(reference: String, messageId: Int) {
        requirePositiveMessageId(messageId)
        gateway.removeReaction(reference, messageId)
    }

    override fun markRead(reference: String) = gateway.markRead(reference)

    override fun sendChatAction(reference: String, activity: ChatActivity) =
        gateway.sendChatAction(reference, activity)

    private fun requireNotBlank(text: String) {
        require(text.isNotBlank()) { "message text must not be blank" }
    }

    private fun requireMessageIds(messageIds: List<Int>) {
        require(messageIds.isNotEmpty()) { "at least one message id is required" }
        require(messageIds.all { it > 0 }) { "message ids must be positive but were $messageIds" }
    }

    private fun requirePositiveMessageId(messageId: Int) {
        require(messageId > 0) { "message id must be positive but was $messageId" }
    }
}
