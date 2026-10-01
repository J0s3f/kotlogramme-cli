package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DownloadedMedia
import com.github.badoualy.telegram.api.MediaKind
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer
import com.github.badoualy.telegram.api.UploadedFile
import org.kotlogramme.cli.application.port.spi.UploadProgress
import org.kotlogramme.cli.application.port.spi.UploadProgressSlot
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * The real [FacadeMediaOperations], delegating straight to the facade client.
 *
 * A path upload is one blocking `mediaSend`, so its progress is a counter the facade fills in and a
 * bar polls from another thread; a stream upload calls back after every chunk instead. Both are
 * wired to the same slot, and both are skipped entirely when nobody is watching, so a run with the
 * bar off stays on the facade's own path.
 */
internal class KotlogramMediaOperations(private val client: TelegramClient) : FacadeMediaOperations {
    override fun sendFile(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressSlot,
    ): Message =
        client.mediaSend(
            peer,
            path,
            kind = if (asPhoto) MediaKind.PHOTO else MediaKind.DOCUMENT,
            caption = caption,
            silent = silent,
            replyToMsgId = replyToMessageId,
            progressHandle = progress.pathHandle(client, path),
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
        progress: UploadProgressSlot,
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
            progressHandle = progress.pathHandle(client, path),
        )

    override fun uploadStream(
        data: InputStream,
        name: String,
        size: Long,
        progress: UploadProgressSlot,
    ): UploadedFile {
        if (!progress.isWatched) return client.uploadStream(data, name, size)
        // The facade calls back after every chunk, so the newest reading is all the bar has to read.
        val latest = AtomicReference<UploadProgress?>(null)
        progress.follow { latest.get() }
        return client.uploadStream(data, name, size, onProgress = { latest.set(it.toUploadProgress()) })
    }

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

    /**
     * Reserves a facade progress slot for the upload of [path] and hands it to the slot, or returns
     * null when no bar is watching, which is grammers' own untracked upload.
     */
    private fun UploadProgressSlot.pathHandle(client: TelegramClient, path: Path): Long? {
        if (!isWatched) return null
        val handle = client.uploadProgressBegin(Files.size(path))
        follow { client.uploadProgress(handle).toUploadProgress() }
        return handle
    }
}
