package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.Message as FacadeMessage
import com.github.badoualy.telegram.api.StickerSetResult
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeStickerOperations], delegating straight to the facade client. */
internal class KotlogramStickerOperations(private val client: TelegramClient) : FacadeStickerOperations {
    override fun sets(): AllStickers = client.messagesGetAllStickers()

    override fun set(id: Long?, accessHash: Long?, shortName: String?): StickerSetResult =
        client.messagesGetStickerSet(id = id, accessHash = accessHash, shortName = shortName)

    override fun send(
        peer: TelegramPeer,
        id: Long?,
        accessHash: Long?,
        shortName: String?,
        index: Int,
        replyToMessageId: Int?,
        silent: Boolean,
    ): FacadeMessage = client.messagesSendSticker(
        peer = peer,
        shortName = shortName,
        id = id,
        accessHash = accessHash,
        index = index,
        replyToMsgId = replyToMessageId,
        silent = silent,
    )
}
