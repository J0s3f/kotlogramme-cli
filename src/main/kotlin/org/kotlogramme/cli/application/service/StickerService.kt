package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.application.port.spi.StickerGateway
import org.kotlogramme.cli.domain.StickerSet

/** Lists the installed sticker sets and reads one, rejecting a blank reference before the gateway. */
class StickerService(private val gateway: StickerGateway) : Stickers {
    override fun sets(): List<StickerSet> = gateway.sets()

    override fun set(reference: String): StickerSet {
        require(reference.isNotBlank()) { "reference must not be blank" }
        return gateway.set(reference)
    }
}
