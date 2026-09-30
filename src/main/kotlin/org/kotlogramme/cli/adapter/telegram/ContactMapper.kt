package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.Contact

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
