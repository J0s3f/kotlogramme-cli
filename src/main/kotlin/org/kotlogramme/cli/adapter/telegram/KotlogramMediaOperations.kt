package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DownloadedMedia
import com.github.badoualy.telegram.api.MediaKind
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer
import com.github.badoualy.telegram.api.UploadedFile
import java.io.InputStream
import java.nio.file.Path

/** The real [FacadeMediaOperations], delegating straight to the facade client. */
internal class KotlogramMediaOperations(private val client: TelegramClient) : FacadeMediaOperations {
    override fun sendFile(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        client.mediaSend(
            peer,
            path,
            kind = if (asPhoto) MediaKind.PHOTO else MediaKind.DOCUMENT,
            caption = caption,
            silent = silent,
            replyToMsgId = replyToMessageId,
        )

    override fun sendVideo(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        client.mediaSend(
            peer,
            path,
            kind = MediaKind.VIDEO,
            caption = caption,
            durationSeconds = durationSeconds,
            width = width,
            height = height,
            silent = silent,
            replyToMsgId = replyToMessageId,
        )


    override fun uploadStream(data: InputStream, name: String): UploadedFile = client.uploadStream(data, name)

    override fun sendUploaded(
        peer: TelegramPeer,
        file: UploadedFile,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        client.mediaSend(
            peer,
            file,
            kind = if (asPhoto) MediaKind.PHOTO else MediaKind.DOCUMENT,
            caption = caption,
            silent = silent,
            replyToMsgId = replyToMessageId,
        )

    override fun sendUrl(
        peer: TelegramPeer,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        client.mediaSendUrl(
            peer,
            url,
            asPhoto = asPhoto,
            caption = caption,
            silent = silent,
            replyToMsgId = replyToMessageId,
        )

    override fun copyMedia(
        peer: TelegramPeer,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        client.mediaCopy(
            destination = peer,
            messageId = fromMessageId,
            source = peer,
            caption = caption,
            silent = silent,
            replyToMsgId = replyToMessageId,
        )

    override fun download(peer: TelegramPeer, messageId: Int, target: Path): DownloadedMedia =
        client.downloadMedia(peer, messageId, target)

    override fun message(peer: TelegramPeer, messageId: Int): Message? =
        client.messagesGetMessages(peer, listOf(messageId)).firstOrNull()
}
