package org.kotlogramme.cli.domain

/** A member of a chat, as a terminal client lists them. */
data class Participant(
    val id: Long,
    val displayName: String,
    val username: String?,
    /** The facade's role name, for example `creator`, `admin` or `member`. */
    val role: String,
) {
    val isPrivileged: Boolean
        get() = role == "creator" || role == "admin"
}
