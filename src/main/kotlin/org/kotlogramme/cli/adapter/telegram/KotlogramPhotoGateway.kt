package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.PhotoGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/**
 * The [PhotoGateway] backed by the kotlogramme facade.
 *
 * References are resolved through the same [ChatReferenceResolver] the rest of the stack uses. A
 * chat photo is a message and is mapped as one; a profile photo has its own shape.
 */
internal class KotlogramPhotoGateway(
    private val operations: FacadePhotoOperations,
    private val resolver: ChatReferenceResolver,
) : PhotoGateway {
    override fun chatPhotos(reference: String, limit: Int): List<Message> =
        operations.chatPhotos(resolver.resolve(reference), limit).map { it.toMessage() }

    override fun profilePhotos(reference: String, limit: Int): List<Photo> =
        operations.profilePhotos(resolver.resolve(reference), limit).map { it.toPhoto() }
}
