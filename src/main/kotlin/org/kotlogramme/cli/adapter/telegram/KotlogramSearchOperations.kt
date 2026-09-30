package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeSearchOperations], delegating straight to the facade client. */
internal class KotlogramSearchOperations(private val client: TelegramClient) : FacadeSearchOperations {
    override fun search(peer: TelegramPeer, query: String, limit: Int): List<Message> =
        client.messagesSearch(peer, query, limit = limit)

    override fun total(peer: TelegramPeer, query: String): Int = client.messagesSearchTotal(peer, query)

    override fun searchGlobal(query: String, limit: Int): List<Message> =
        client.messagesSearchGlobal(query, limit = limit)

    override fun totalGlobal(query: String): Int = client.messagesSearchGlobalTotal(query)
}
