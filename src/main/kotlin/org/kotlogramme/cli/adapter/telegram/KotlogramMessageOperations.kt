package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeMessageOperations], delegating straight to the facade client. */
internal class KotlogramMessageOperations(private val client: TelegramClient) : FacadeMessageOperations {
    override fun history(peer: TelegramPeer, limit: Int, beforeMessageId: Int?): List<Message> =
        client.messagesGetHistory(peer, limit = limit, offsetId = beforeMessageId)
}
