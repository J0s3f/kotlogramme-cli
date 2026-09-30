package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.application.port.spi.StickerGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.StickerSet

/** Lists the installed sticker sets, reads one, and sends a sticker, validating before the gateway. */
class StickerService(private val gateway: StickerGateway) : Stickers {
    override fun sets(): List<StickerSet> = gateway.sets()

    override fun set(reference: String): StickerSet {
        require(reference.isNotBlank()) { "reference must not be blank" }
        return gateway.set(reference)
    }

    override fun send(
        chatReference: String,
        setReference: String,
        index: Int,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        require(chatReference.isNotBlank()) { "chat reference must not be blank" }
        require(setReference.isNotBlank()) { "sticker set reference must not be blank" }
        require(index >= 0) { "index must not be negative but was $index" }
        return gateway.send(chatReference, setReference, index, replyToMessageId, silent)
    }
}
