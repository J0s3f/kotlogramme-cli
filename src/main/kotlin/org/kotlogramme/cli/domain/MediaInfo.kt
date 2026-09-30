package org.kotlogramme.cli.domain

/**
 * What a message's attachment actually is.
 *
 * The facade projects a flat media shape whose [kind] names the variant and says which of the other
 * fields are populated; this keeps the subset the terminal renders. Every detail beyond the kind is
 * optional, because a kind that does not carry it leaves it `null`.
 *
 * [width]/[height] are the thumbnail's pixel size, which for a video is a scaled frame rather than
 * the video itself; [resolutionWidth]/[resolutionHeight] carry the real resolution when the media
 * has one.
 */
data class MediaInfo(
    val kind: String,
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val sizeBytes: Long? = null,
    val name: String? = null,
)
