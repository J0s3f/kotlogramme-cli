package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.StickerSet

/** List the installed sticker sets, read one, and send a sticker from one. */
interface Stickers {
    fun sets(): List<StickerSet>

    fun set(reference: String): StickerSet

    /** Sends the sticker at [index] of [setReference] to [chatReference]. */
    fun send(
        chatReference: String,
        setReference: String,
        index: Int,
        replyToMessageId: Int? = null,
        silent: Boolean = false,
    ): Message
}
