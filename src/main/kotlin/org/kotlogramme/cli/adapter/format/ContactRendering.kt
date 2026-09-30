package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Contact

/**
 * Renders the contact list.
 *
 * The row is plain data so the same projection feeds every [Output] format; the username and phone
 * are their own columns rather than decoration.
 */
internal val CONTACT_HEADERS = listOf("id", "name", "username", "phone")

/** Prints the contacts as the configured output format. */
fun Output.renderContacts(contacts: List<Contact>) {
    table(CONTACT_HEADERS, contacts.map(::contactRow))
}

/** The row for one [contact]; pure so it can be snapshot-tested without an [Output]. */
internal fun contactRow(contact: Contact): List<String> = listOf(
    contact.id.toString(),
    contact.displayName,
    contact.username.orEmpty(),
    contact.phoneNumber.orEmpty(),
)
