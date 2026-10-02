package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.MessageSearchFilter
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeSearchOperations], delegating straight to the facade client. */
internal class KotlogramSearchOperations(private val client: TelegramClient) : FacadeSearchOperations {
    override fun search(peer: TelegramPeer, query: String, limit: Int, offsetId: Int?): List<Message> =
        client.messagesSearch(peer, query, limit = limit, offsetId = offsetId)

    override fun total(peer: TelegramPeer, query: String): Int = client.messagesSearchTotal(peer, query)

    override fun searchGlobal(query: String, limit: Int, offsetId: Int?): List<Message> =
        client.messagesSearchGlobal(query, limit = limit, offsetId = offsetId)

    override fun totalGlobal(query: String): Int = client.messagesSearchGlobalTotal(query)

    /**
     * A file listing is the same search with no query text and a media filter.
     *
     * The empty query is not a placeholder: Telegram's own clients list a chat's files by searching
     * for nothing in particular with a media filter, and grammers forwards the query as it is, so
     * the filter is what decides the result.
     */
    override fun searchFiles(peer: TelegramPeer, filter: MessageSearchFilter, limit: Int, offsetId: Int?): List<Message> =
        client.messagesSearch(peer, query = "", limit = limit, offsetId = offsetId, filter = filter)

    override fun totalFiles(peer: TelegramPeer, filter: MessageSearchFilter): Int =
        client.messagesSearchTotal(peer, query = "", filter = filter)
}
