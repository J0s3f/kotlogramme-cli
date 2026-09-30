package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.Contact

/** Lists and searches contacts and blocks or unblocks a peer, validating the arguments first. */
class ContactsService(private val gateway: ContactGateway) : Contacts {
    override fun list(limit: Int): List<Contact> {
        requirePositive(limit)
        return gateway.contacts(limit)
    }

    override fun search(query: String, limit: Int): List<Contact> {
        require(query.isNotBlank()) { "query must not be blank" }
        requirePositive(limit)
        return gateway.search(query, limit)
    }

    override fun block(reference: String) {
        gateway.block(reference)
    }

    override fun unblock(reference: String) {
        gateway.unblock(reference)
    }

    private fun requirePositive(limit: Int) {
        require(limit > 0) { "limit must be positive but was $limit" }
    }
}
