package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/** Searches messages in one chat or globally, validating the query and limit first. */
class SearchMessagesService(private val gateway: MessageSearchGateway) : SearchMessages {
    override fun search(reference: String?, query: String, limit: Int, cursor: String?): List<Message> {
        require(query.isNotBlank()) { "query must not be blank" }
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.search(reference, query, limit, cursor)
    }

    override fun total(reference: String?, query: String): Int {
        require(query.isNotBlank()) { "query must not be blank" }
        return gateway.total(reference, query)
    }

    /**
     * A media-kind listing carries no text to search for, so the blank-query rule the text search
     * needs does not apply here: an empty query with a filter is how Telegram lists a chat's files.
     *
     * [MediaFileKind.ALL] is the union of the kinds below, because Telegram filters one media kind
     * per search and has no "every file" filter: each kind is asked for separately and the answers
     * are merged newest first. A message the server reports under two of them - a track that is both
     * a document and music - is listed once.
     */
    override fun files(reference: String, kind: MediaFileKind, limit: Int, cursor: String?): List<Message> {
        require(limit > 0) { "limit must be positive but was $limit" }
        if (kind != MediaFileKind.ALL) return gateway.files(reference, kind, limit, cursor)
        return ALL_KINDS
            .flatMap { gateway.files(reference, it, limit, cursor) }
            .distinctBy(Message::id)
            .sortedByDescending(Message::id)
            .take(limit)
    }

    /**
     * For [MediaFileKind.ALL] this is the sum of the kinds' own totals. A file the server counts under
     * two filters would be counted twice; there is no single request that would count it once.
     */
    override fun fileTotal(reference: String, kind: MediaFileKind): Int =
        if (kind == MediaFileKind.ALL) {
            ALL_KINDS.sumOf { gateway.fileTotal(reference, it) }
        } else {
            gateway.fileTotal(reference, kind)
        }

    private companion object {
        /**
         * The kinds whose filters between them cover a chat's files.
         *
         * [MediaFileKind.PHOTOS] and [MediaFileKind.VIDEO] are left out because
         * [MediaFileKind.PHOTO_VIDEO] already covers both, and [MediaFileKind.ANIMATION] because it
         * shares the GIF filter, so asking for them would only repeat a search.
         */
        val ALL_KINDS = listOf(
            MediaFileKind.PHOTO_VIDEO,
            MediaFileKind.DOCUMENT,
            MediaFileKind.GIF,
            MediaFileKind.VOICE,
            MediaFileKind.MUSIC,
        )
    }
}
