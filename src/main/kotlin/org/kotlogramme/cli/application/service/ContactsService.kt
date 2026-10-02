package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport

/** Lists and searches contacts and mutates the contact list, validating the arguments first. */
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

    override fun blocked(limit: Int, cursor: String?, all: Boolean): List<BlockedContact> {
        requirePositive(limit)
        return gateway.blocked(limit, cursor, all)
    }

    override fun import(contacts: List<ContactToImport>): ContactImportSummary {
        require(contacts.isNotEmpty()) { "at least one contact is required" }
        contacts.forEach(::requireWellFormed)
        return gateway.import(contacts)
    }

    override fun delete(reference: String) {
        require(reference.isNotBlank()) { "the peer reference must not be blank" }
        gateway.delete(reference)
    }

    private fun requireWellFormed(contact: ContactToImport) {
        require(contact.phone.isNotBlank()) { "a contact phone number must not be blank" }
        require(contact.firstName.isNotBlank() || contact.lastName.isNotBlank()) {
            "a contact needs a first or last name"
        }
    }

    private fun requirePositive(limit: Int) {
        require(limit > 0) { "limit must be positive but was $limit" }
    }
}
