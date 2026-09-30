package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.Message

/** Ask an inline bot and send one of its results. */
interface InlineBots {
    fun query(bot: String, query: String, reference: String?): InlineQuery

    fun send(reference: String, queryId: Long, resultId: String): Message?
}
