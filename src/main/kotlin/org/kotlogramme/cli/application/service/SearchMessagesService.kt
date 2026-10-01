package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/** Searches messages in one chat or globally, validating the query and limit first. */
class SearchMessagesService(private val gateway: MessageSearchGateway) : SearchMessages {
    override fun search(reference: String?, query: String, limit: Int): List<Message> {
        require(query.isNotBlank()) { "query must not be blank" }
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.search(reference, query, limit)
    }

    override fun total(reference: String?, query: String): Int {
        require(query.isNotBlank()) { "query must not be blank" }
        return gateway.total(reference, query)
    }

    /**
     * A media-kind listing carries no text to search for, so the blank-query rule the text search
     * needs does not apply here: an empty query with a filter is how Telegram lists a chat's files.
     */
    override fun files(reference: String, kind: MediaFileKind, limit: Int): List<Message> {
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.files(reference, kind, limit)
    }

    override fun fileTotal(reference: String, kind: MediaFileKind): Int = gateway.fileTotal(reference, kind)
}
