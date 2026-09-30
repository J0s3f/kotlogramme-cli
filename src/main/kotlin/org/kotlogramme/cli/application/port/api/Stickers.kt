package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.StickerSet

/** List the installed sticker sets and read one of them. */
interface Stickers {
    fun sets(): List<StickerSet>

    fun set(reference: String): StickerSet
}
