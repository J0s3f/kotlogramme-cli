package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ContactImport
import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport

/**
 * The [ContactGateway] backed by the kotlogramme facade.
 *
 * The facade's contacts answer is not paginated, so the requested limit is applied after mapping.
 * A search answer carries the viewer's own matches and the directory's, in that order; both are
 * kept. Blocking and unblocking resolve the reference through the seam the rest of the adapter uses.
 * An import assigns each contact a client id by its position, which is all the layer needs to match
 * an answer back to a request.
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

    override fun blocked(limit: Int, cursor: String?, all: Boolean): List<BlockedContact> =
        operations.blocked(
            offset = cursor?.let(ListingCursor::parseDecimal) ?: 0,
            limit = limit,
            all = all,
        ).blocked
            .take(limit)
            .map { it.toBlockedContact() }

    override fun import(contacts: List<ContactToImport>): ContactImportSummary {
        val answer = operations.importContacts(
            contacts.mapIndexed { index, contact ->
                ContactImport(
                    clientId = (index + 1).toLong(),
                    phone = contact.phone,
                    firstName = contact.firstName,
                    lastName = contact.lastName,
                )
            },
        )
        return ContactImportSummary(
            imported = answer.imported.mapNotNull { it.user?.toContact() },
            retryCount = answer.retryContacts.size,
        )
    }

    override fun delete(reference: String) =
        operations.deleteContacts(listOf(operations.resolve(reference)))
}
