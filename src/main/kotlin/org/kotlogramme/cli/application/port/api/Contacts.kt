package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport

/** The account's contacts, and blocking, unblocking, importing or deleting a peer. */
interface Contacts {
    fun list(limit: Int): List<Contact>

    fun search(query: String, limit: Int): List<Contact>

    fun block(reference: String)

    fun unblock(reference: String)

    fun blocked(limit: Int): List<BlockedContact>

    fun import(contacts: List<ContactToImport>): ContactImportSummary

    fun delete(reference: String)
}
