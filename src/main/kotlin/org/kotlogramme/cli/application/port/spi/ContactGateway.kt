package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport

/** The contact operations the facade exposes, in domain terms. */
interface ContactGateway {
    fun contacts(limit: Int): List<Contact>

    fun search(query: String, limit: Int): List<Contact>

    fun block(reference: String)

    fun unblock(reference: String)

    /**
     * Lists the account's blocked peers, newest block first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun blocked(limit: Int, cursor: String? = null, all: Boolean = false): List<BlockedContact>

    /** Imports [contacts], answering what Telegram saved and what it asked to retry. */
    fun import(contacts: List<ContactToImport>): ContactImportSummary

    /** Removes [reference] from the account's saved contacts; the peer is not blocked. */
    fun delete(reference: String)
}
