package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/** The photo operations the facade exposes, in domain terms. */
interface PhotoGateway {
    /**
     * Lists the messages that record a change to the chat's photo, most recent first.
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
