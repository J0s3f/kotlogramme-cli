package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.InlineQueryResults
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade inline-bot calls the inline gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramInlineGateway] can be exercised without a live client. Each method
 * mirrors one facade operation; the facade models are mapped to the domain at the gateway boundary.
 */
internal interface FacadeInlineOperations {
    /**
     * Asks [bot] for [query], optionally in the context of [peer], which is `inlineQuery`.
     * `peer` is null when the caller typed the query without a chosen chat.
     */
    fun query(bot: TelegramPeer, query: String, peer: TelegramPeer?): InlineQueryResults

    /**
     * Sends the inline result [resultId] of the answer [queryId] to [peer], which is
     * `sendInlineBotResult`; `null` when the facade could not name the sent message.
     */
    fun send(peer: TelegramPeer, queryId: Long, resultId: String): Message?
}
