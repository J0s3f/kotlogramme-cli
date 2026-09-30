package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Contact

/** The account's contacts, and blocking or unblocking a peer. */
interface Contacts {
    fun list(limit: Int): List<Contact>

    fun search(query: String, limit: Int): List<Contact>

    fun block(reference: String)

    fun unblock(reference: String)
}
