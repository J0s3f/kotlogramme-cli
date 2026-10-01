package org.kotlogramme.cli.domain

import java.time.Instant

/** A person in the account's contact list. */
data class Contact(
    val id: Long,
    val displayName: String,
    val username: String?,
    val phoneNumber: String?,
)

/** A peer the account has blocked, with the date Telegram recorded the block. */
data class BlockedContact(
    val id: Long,
    val displayName: String,
    val username: String?,
    /** When the peer was blocked, as Telegram reports it. */
    val blockedAt: Instant,
)

/** One contact to import: a phone number and the name to save it under. */
data class ContactToImport(
    val phone: String,
    val firstName: String,
    val lastName: String,
)

/** What an import actually did: the contacts Telegram saved and the ids it asked to retry. */
data class ContactImportSummary(
    val imported: List<Contact>,
    val retryCount: Int,
)
