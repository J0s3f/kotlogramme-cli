package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo

/** The photo operations the facade exposes, in domain terms. */
interface PhotoGateway {
    fun chatPhotos(reference: String, limit: Int): List<Message>

    fun profilePhotos(reference: String, limit: Int): List<Photo>
}
