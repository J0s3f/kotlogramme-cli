package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.application.port.spi.PhotoGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/**
 * The [PhotoGateway] backed by the kotlogramme facade.
 *
 * References are resolved through the same [ChatReferenceResolver] the rest of the stack uses. A
 * chat-photo change is a message and is mapped as one; a profile photo has its own shape.
 */
internal class KotlogramPhotoGateway(
    private val operations: FacadePhotoOperations,
    private val resolver: ChatReferenceResolver,
) : PhotoGateway {
    override fun chatPhotos(reference: String, limit: Int, cursor: String?, all: Boolean): List<Message> =
        operations.chatPhotos(
            peer = resolver.resolve(reference),
            limit = limit,
            offsetId = cursor?.let(ListingCursor::parseDecimal),
            all = all,
        ).map { it.toMessage() }

    override fun profilePhotos(reference: String, limit: Int, cursor: String?, all: Boolean): List<Photo> =
        operations.profilePhotos(
            peer = resolver.resolve(reference),
            limit = limit,
            offset = cursor?.let(ListingCursor::parseDecimal),
            all = all,
        ).map { it.toPhoto() }
}
