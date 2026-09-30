package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.Message

/**
 * The inline-bot operations the facade exposes, in domain terms.
 *
 * Querying and sending are separate because Telegram expires a query id, so the caller asks and
 * then sends the result it chose from that same answer.
 */
interface InlineGateway {
    /** Asks [bot] for [query], optionally in the context of the chat [reference]. */
    fun query(bot: String, query: String, reference: String?): InlineQuery

    /** Sends one result of an answer; `null` when the facade could not name the sent message. */
    fun send(reference: String, queryId: Long, resultId: String): Message?
}
