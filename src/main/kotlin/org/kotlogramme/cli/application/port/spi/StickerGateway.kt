package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.StickerSet

/** The sticker operations the facade exposes, in domain terms. */
interface StickerGateway {
    /** The account's installed sets, which is `messagesGetAllStickers`. */
    fun sets(): List<StickerSet>

    /** One set by short name, or by `id:accessHash`, which is `messagesGetStickerSet`. */
    fun set(reference: String): StickerSet
}
