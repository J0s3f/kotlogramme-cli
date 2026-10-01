package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/**
 * A peer's photos: the chat's own photo messages and a user's profile photos.
 *
 * A chat photo is a message, so it renders and downloads like any other media; a profile photo is
 * not addressable by message id and is listed only.
 */
interface Photos {
    /** Lists the messages that carry a chat photo, most recent first. */
    fun chatPhotos(reference: String, limit: Int): List<Message>

    /** Lists a user's profile photos, most recent first. */
    fun profilePhotos(reference: String, limit: Int): List<Photo>
}
