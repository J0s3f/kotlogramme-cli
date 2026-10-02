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

    /**
     * Lists the account's blocked peers, newest block first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun blocked(limit: Int, cursor: String? = null, all: Boolean = false): List<BlockedContact>

    fun import(contacts: List<ContactToImport>): ContactImportSummary

    fun delete(reference: String)
}
