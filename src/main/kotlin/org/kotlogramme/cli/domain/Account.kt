package org.kotlogramme.cli.domain

/** The signed-in Telegram account, reduced to the fields a terminal client shows. */
data class Account(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phoneNumber: String?,
) {
    val displayName: String
        get() = listOf(firstName, lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { username ?: id.toString() }
}
