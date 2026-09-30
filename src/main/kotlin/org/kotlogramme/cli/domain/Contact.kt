package org.kotlogramme.cli.domain

/** A person in the account's contact list. */
data class Contact(
    val id: Long,
    val displayName: String,
    val username: String?,
    val phoneNumber: String?,
)
