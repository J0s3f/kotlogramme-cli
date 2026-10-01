package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatAction
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade message-writing calls the write gateway needs, narrowed to a seam a test can
 * implement.
 *
 * This exists so [KotlogramMessageWriteGateway] can be exercised without a live client. Each
 * method mirrors one facade operation; the facade models are mapped to the domain at the gateway
 * boundary.
 */
internal interface FacadeMessageWriteOperations {
    /** Sends [text] to [peer], which is `messagesSendMessage`. */
    fun send(peer: TelegramPeer, text: String, replyToMessageId: Int?, silent: Boolean): Message

    /**
     * Replaces the text of message [id] in [peer] and returns the stored message, which is
     * `messagesEditMessage` followed by reading the message back.
     */
    fun edit(peer: TelegramPeer, id: Int, text: String): Message

    /** Deletes messages [ids] in [peer], returning how many were removed, which is `messagesDeleteMessages`. */
    fun delete(peer: TelegramPeer, ids: Collection<Int>): Int

    /** Forwards messages [ids] from [fromPeer] to [toPeer], which is `messagesForwardMessages`. */
    fun forward(toPeer: TelegramPeer, ids: Collection<Int>, fromPeer: TelegramPeer): List<Message?>

    /** Pins message [id] in [peer], which is `messagesPinMessage`. */
    fun pin(peer: TelegramPeer, id: Int)

    /** Unpins message [id] in [peer], which is `messagesUnpinMessage`. */
    fun unpin(peer: TelegramPeer, id: Int)

    /** Reacts to message [id] in [peer] with [emoji], which is `messagesSendReaction`. */
    fun react(peer: TelegramPeer, id: Int, emoji: String)

    /** Removes the reaction on message [id] in [peer], which is `messagesRemoveReaction`. */
    fun removeReaction(peer: TelegramPeer, id: Int)

    /** Marks every message in [peer] as read, which is `messagesReadHistory`. */
    fun markRead(peer: TelegramPeer)

    /** Reports [action] in [peer], which is `actionsSendChatAction`. */
    fun sendChatAction(peer: TelegramPeer, action: ChatAction)
}
