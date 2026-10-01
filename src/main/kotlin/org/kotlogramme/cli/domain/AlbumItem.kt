package org.kotlogramme.cli.domain

import java.nio.file.Path

/**
 * One item of an album being sent.
 *
 * The facade's album carries only a path, a caption and whether it goes as a photo, so a video is
 * sent as a document rather than a streamable video: the album surface has nowhere to put duration
 * or resolution.
 */
data class AlbumItem(
    val path: Path,
    val caption: String,
    val asPhoto: Boolean,
)
