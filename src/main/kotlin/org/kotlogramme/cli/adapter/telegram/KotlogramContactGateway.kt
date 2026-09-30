package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.Contact

/**
 * The [ContactGateway] backed by the kotlogramme facade.
 *
 * The facade's contacts answer is not paginated, so the requested limit is applied after mapping.
 * A search answer carries the viewer's own matches and the directory's, in that order; both are
 * kept. Blocking and unblocking resolve the reference through the seam the rest of the adapter uses.
 */
internal class KotlogramContactGateway(private val operations: FacadeContactOperations) : ContactGateway {
    override fun contacts(limit: Int): List<Contact> =
        operations.contacts(hash = 0).contacts
            .mapNotNull { it.user }
            .take(limit)
            .map { it.toContact() }

    override fun search(query: String, limit: Int): List<Contact> =
        operations.search(query, limit).let { it.myResults + it.results }
            .mapNotNull { it.user }
            .map { it.toContact() }

    override fun block(reference: String) = operations.block(operations.resolve(reference))

    override fun unblock(reference: String) = operations.unblock(operations.resolve(reference))
}
