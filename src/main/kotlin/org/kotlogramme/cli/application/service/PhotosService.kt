package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Photos
import org.kotlogramme.cli.application.port.spi.PhotoGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/** Lists a chat's photo-change history and a user's profile photos, rejecting a non-positive limit first. */
class PhotosService(private val gateway: PhotoGateway) : Photos {
    override fun chatPhotos(reference: String, limit: Int): List<Message> {
        requirePositive(limit)
        return gateway.chatPhotos(reference, limit)
    }

    override fun profilePhotos(reference: String, limit: Int): List<Photo> {
        requirePositive(limit)
        return gateway.profilePhotos(reference, limit)
    }

    private fun requirePositive(limit: Int) {
        require(limit > 0) { "limit must be positive but was $limit" }
    }
}
