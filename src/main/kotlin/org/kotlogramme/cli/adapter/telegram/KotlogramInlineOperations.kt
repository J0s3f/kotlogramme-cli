package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.InlineQueryResults
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeInlineOperations], delegating straight to the facade client. */
internal class KotlogramInlineOperations(private val client: TelegramClient) : FacadeInlineOperations {
    override fun query(bot: TelegramPeer, query: String, peer: TelegramPeer?): InlineQueryResults =
        client.inlineQuery(bot, query, peer)

    override fun send(peer: TelegramPeer, queryId: Long, resultId: String): Message? =
        client.sendInlineBotResult(peer, queryId, resultId)
}
