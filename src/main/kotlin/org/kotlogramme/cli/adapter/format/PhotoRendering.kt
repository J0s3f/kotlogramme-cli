package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Photo

/**
 * Renders a user's profile photos.
 *
 * The row is plain data so every [Output] format carries the same fields. A profile photo has no
 * message id, so [id] is the photo's own id and `dc` the data centre it lives in; `size` is the
 * exact byte count of the largest thumbnail, as the file listing's is.
 */
internal val PHOTO_HEADERS = listOf("id", "dc", "size", "width", "height", "spoiler")

/** Prints the profile photos as the configured output format. */
fun Output.renderPhotos(photos: List<Photo>) {
    table(PHOTO_HEADERS, photos.map(::photoRow))
}

/** The row for one [photo]; pure so it can be snapshot-tested without an [Output]. */
internal fun photoRow(photo: Photo): List<String> = listOf(
    photo.id.toString(),
    photo.dcId?.toString().orEmpty(),
    photo.sizeBytes.toString(),
    photo.width?.toString().orEmpty(),
    photo.height?.toString().orEmpty(),
    if (photo.spoiler) "yes" else "",
)
