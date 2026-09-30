package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Message

/** The message-reading operations the facade exposes, in domain terms. */
interface MessageGateway {
    /**
     * The newest [limit] messages of the chat [reference] names, oldest first. When
     * [beforeMessageId] is set, only messages older than it are returned.
     */
    fun history(reference: String, limit: Int, beforeMessageId: Int?): List<Message>
}
