package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Contact

/** The contact operations the facade exposes, in domain terms. */
interface ContactGateway {
    fun contacts(limit: Int): List<Contact>

    fun search(query: String, limit: Int): List<Contact>

    fun block(reference: String)

    fun unblock(reference: String)
}
