package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.Message

/**
 * The [MessageSearchGateway] backed by the kotlogramme facade.
 *
 * A null reference is a global search; otherwise the reference is resolved through the same
 * [ChatReferenceResolver] the rest of the adapter uses. Results are mapped with the shared message
 * mapper.
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
}
