package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import java.time.format.DateTimeFormatter

/**
 * Renders the contact list.
 *
 * The row is plain data so the same projection feeds every [Output] format; the username and phone
 * are their own columns rather than decoration.
 */
internal val CONTACT_HEADERS = listOf("id", "name", "username", "phone")

/** The columns the blocked listing shows; the block date is its own column, not decoration. */
internal val BLOCKED_HEADERS = listOf("id", "name", "username", "blocked")

/** Prints the contacts as the configured output format. */
fun Output.renderContacts(contacts: List<Contact>) {
    table(CONTACT_HEADERS, contacts.map(::contactRow))
}

/** Prints the blocked peers as the configured output format. */
fun Output.renderBlockedContacts(contacts: List<BlockedContact>) {
    table(BLOCKED_HEADERS, contacts.map(::blockedContactRow))
}

/** The row for one [contact]; pure so it can be snapshot-tested without an [Output]. */
internal fun contactRow(contact: Contact): List<String> = listOf(
    contact.id.toString(),
    contact.displayName,
    contact.username.orEmpty(),
    contact.phoneNumber.orEmpty(),
)

/** The row for one blocked [contact]; pure so it can be snapshot-tested without an [Output]. */
internal fun blockedContactRow(contact: BlockedContact): List<String> = listOf(
    contact.id.toString(),
    contact.displayName,
    contact.username.orEmpty(),
    DateTimeFormatter.ISO_INSTANT.format(contact.blockedAt),
)
