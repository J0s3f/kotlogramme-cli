package org.kotlogramme.cli.application.port.spi

import java.nio.file.Path

/** What a local file should be sent as. */
enum class MediaKindHint {
    PHOTO,
    VIDEO,
    DOCUMENT,
}

/**
 * What a file should be sent as, and the metadata Telegram needs to describe it.
 *
 * [durationSeconds], [width] and [height] are only meaningful for a [MediaKindHint.VIDEO], and are
 * null when the file could not be read: an unrecognised container, or a format this build does not
 * parse. A null is not an error — it means "send it, but without the metadata".
 */
data class MediaProbeResult(
    val kind: MediaKindHint,
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * Decides how to send a local file by looking at it.
 *
 * This is an outbound concern: the file lives on the caller's machine, and nothing above this port
 * should know about file extensions or container formats.
 */
interface MediaProbe {
    fun probe(path: Path): MediaProbeResult
}
