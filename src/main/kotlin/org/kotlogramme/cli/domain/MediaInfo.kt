package org.kotlogramme.cli.domain

/**
 * What a message's attachment actually is.
 *
 * The facade projects a flat media shape whose [kind] names the variant and says which of the other
 * fields are populated; this keeps the subset the terminal renders. Every detail beyond the kind is
 * optional, because a kind that does not carry it leaves it `null`.
 */
data class MediaInfo(
    val kind: String,
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val name: String? = null,
)
