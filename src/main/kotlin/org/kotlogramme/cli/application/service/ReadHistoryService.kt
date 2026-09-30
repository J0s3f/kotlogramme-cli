package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.spi.MessageGateway
import org.kotlogramme.cli.domain.Message

/** Reads a page of a chat's history, rejecting a limit that would ask for nothing or less. */
class ReadHistoryService(private val gateway: MessageGateway) : ReadHistory {
    override fun read(reference: String, limit: Int, beforeMessageId: Int?): List<Message> {
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.history(reference, limit, beforeMessageId)
    }
}
