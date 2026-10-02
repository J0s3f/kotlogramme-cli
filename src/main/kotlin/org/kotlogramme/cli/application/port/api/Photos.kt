package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/**
 * A peer's photos: the changes to a chat's own photo, and a user's profile photos.
 *
 * The two are different things and neither is "the photos posted in a chat": those are messages
 * with the photo filter, which the file listing serves. A chat-photo change is a service message, so
 * it renders and downloads like any other media; a profile photo is not addressable by message id
 * and is listed only.
 */
interface Photos {
    /**
     * Lists the messages that record a change to the chat's own photo, most recent first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun chatPhotos(reference: String, limit: Int, cursor: String? = null, all: Boolean = false): List<Message>

    /**
     * Lists a user's profile photos, most recent first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun profilePhotos(reference: String, limit: Int, cursor: String? = null, all: Boolean = false): List<Photo>
}
