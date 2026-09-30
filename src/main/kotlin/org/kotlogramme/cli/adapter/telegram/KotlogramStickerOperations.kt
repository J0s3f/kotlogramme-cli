package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.StickerSetResult
import com.github.badoualy.telegram.api.TelegramClient

/** The real [FacadeStickerOperations], delegating straight to the facade client. */
internal class KotlogramStickerOperations(private val client: TelegramClient) : FacadeStickerOperations {
    override fun sets(): AllStickers = client.messagesGetAllStickers()

    override fun set(id: Long?, accessHash: Long?, shortName: String?): StickerSetResult =
        client.messagesGetStickerSet(id = id, accessHash = accessHash, shortName = shortName)
}
