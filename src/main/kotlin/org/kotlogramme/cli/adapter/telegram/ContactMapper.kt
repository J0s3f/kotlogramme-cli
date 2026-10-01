package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.BlockedPeer
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import java.time.Instant

/**
 * Reduces a facade user to the contact a terminal client lists.
 *
 * A username or phone number is optional, so a user without either keeps a null field rather than a
 * blank one. The display name reuses the same fallback the message mapper uses, so a contact reads
 * the same way wherever it appears.
 */
internal fun User.toContact(): Contact = Contact(
    id = id,
    displayName = displayName(),
    username = username?.takeIf(String::isNotBlank),
    phoneNumber = phone?.takeIf(String::isNotBlank),
)

/**
 * Reduces a facade blocked peer to the row the blocked listing shows.
 *
 * The peer's user object is the readable form; a blocked chat or channel, or a user Telegram did not
 * project, falls back to the peer's own name and username so the row is never nameless.
 */
internal fun BlockedPeer.toBlockedContact(): BlockedContact = BlockedContact(
    id = user?.id ?: peer.id,
    displayName = user?.displayName() ?: peer.name?.takeIf(String::isNotBlank) ?: peer.id.toString(),
    username = (user?.username ?: peer.username)?.takeIf(String::isNotBlank),
    blockedAt = Instant.ofEpochMilli(date),
)
