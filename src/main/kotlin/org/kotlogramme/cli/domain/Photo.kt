package org.kotlogramme.cli.domain

/**
 * A profile photo of a peer.
 *
 * [dcId] is the data centre the photo lives in and is null for a photo that carries no location;
 * [sizeBytes] is the largest thumbnail's size. Unlike a chat photo message, a profile photo has no
 * message id, so it cannot be handed to `download-media`.
 */
data class Photo(
    val id: Long,
    val dcId: Int?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val spoiler: Boolean,
)
