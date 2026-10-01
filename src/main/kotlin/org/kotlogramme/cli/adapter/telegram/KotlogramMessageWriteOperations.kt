package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatAction
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeMessageWriteOperations], delegating straight to the facade client. */
internal class KotlogramMessageWriteOperations(private val client: TelegramClient) : FacadeMessageWriteOperations {
    override fun send(peer: TelegramPeer, text: String, replyToMessageId: Int?, silent: Boolean): Message =
        client.messagesSendMessage(peer, text, replyToMsgId = replyToMessageId, silent = silent)

    override fun edit(peer: TelegramPeer, id: Int, text: String): Message {
        client.messagesEditMessage(peer, id, text)
        return client.messagesGetMessages(peer, listOf(id)).firstOrNull()
            ?: error("the edited message $id could not be read back from the peer")
    }

    override fun delete(peer: TelegramPeer, ids: Collection<Int>): Int = client.messagesDeleteMessages(peer, ids)

    override fun forward(toPeer: TelegramPeer, ids: Collection<Int>, fromPeer: TelegramPeer): List<Message?> =
        client.messagesForwardMessages(toPeer, ids, fromPeer)

    override fun pin(peer: TelegramPeer, id: Int) = client.messagesPinMessage(peer, id)

    override fun unpin(peer: TelegramPeer, id: Int) = client.messagesUnpinMessage(peer, id)

    override fun unpinAll(peer: TelegramPeer) = client.messagesUnpinAllMessages(peer)

    override fun pinned(peer: TelegramPeer): Message? = client.messagesGetPinnedMessage(peer)

    override fun react(peer: TelegramPeer, id: Int, emoji: String) = client.messagesSendReaction(peer, id, emoji)

    override fun removeReaction(peer: TelegramPeer, id: Int) = client.messagesRemoveReaction(peer, id)

    override fun markRead(peer: TelegramPeer) = client.messagesReadHistory(peer)

    override fun sendChatAction(peer: TelegramPeer, action: ChatAction) =
        client.actionsSendChatAction(peer, action)
}
