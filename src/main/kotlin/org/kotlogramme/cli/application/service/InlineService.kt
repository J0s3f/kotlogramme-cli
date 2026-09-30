package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.spi.InlineGateway
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.Message

/**
 * Asks an inline bot and sends one of its results through the [InlineGateway].
 *
 * Malformed input is rejected here: a blank bot, a blank query and a non-positive query id never
 * reach the gateway, so the failure is a clear [IllegalArgumentException] instead of a Telegram
 * error.
 */
class InlineService(private val gateway: InlineGateway) : InlineBots {
    override fun query(bot: String, query: String, reference: String?): InlineQuery {
        require(bot.isNotBlank()) { "bot must not be blank" }
        require(query.isNotBlank()) { "query must not be blank" }
        return gateway.query(bot, query, reference)
    }

    override fun send(reference: String, queryId: Long, resultId: String): Message? {
        require(queryId > 0) { "query id must be positive but was $queryId" }
        return gateway.send(reference, queryId, resultId)
    }
}
