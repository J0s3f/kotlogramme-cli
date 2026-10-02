package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.MessageSearchFilter
import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/**
 * The [MessageSearchGateway] backed by the kotlogramme facade.
 *
 * A null reference is a global search; otherwise the reference is resolved through the same
 * [ChatReferenceResolver] the rest of the adapter uses. Results are mapped with the shared message
 * mapper.
 *
 * A file listing resolves the reference the same way and asks the server for one media kind, because
 * a chat's files are wherever Telegram says they are and not where a history scan has read.
 */
internal class KotlogramMessageSearchGateway(
    private val operations: FacadeSearchOperations,
    private val resolver: ChatReferenceResolver,
) : MessageSearchGateway {
    override fun search(reference: String?, query: String, limit: Int): List<Message> {
        val messages = if (reference == null) {
            operations.searchGlobal(query, limit)
        } else {
            operations.search(resolver.resolve(reference), query, limit)
        }
        return messages.map { it.toMessage() }
    }

    override fun total(reference: String?, query: String): Int =
        if (reference == null) {
            operations.totalGlobal(query)
        } else {
            operations.total(resolver.resolve(reference), query)
        }

    override fun files(reference: String, kind: MediaFileKind, limit: Int): List<Message> =
        operations.searchFiles(resolver.resolve(reference), kind.toFilter(), limit).map { it.toMessage() }

    override fun fileTotal(reference: String, kind: MediaFileKind): Int =
        operations.totalFiles(resolver.resolve(reference), kind.toFilter())

    /**
     * The facade filter [kind] names, looked up by the wire name both sides agree on.
     *
     * An animation shares the GIF filter with a gif, so [MediaFileKind.ANIMATION] lands on the same
     * filter [MediaFileKind.GIF] does, which is what Telegram itself does.
     */
    private fun MediaFileKind.toFilter(): MessageSearchFilter {
        check(this != MediaFileKind.ALL) {
            "MediaFileKind.ALL names no filter; the service expands it before the gateway is asked"
        }
        return requireNotNull(MessageSearchFilter.entries.firstOrNull { it.wire == filter }) {
            "no search filter carries the wire name '$filter'"
        }
    }
}
