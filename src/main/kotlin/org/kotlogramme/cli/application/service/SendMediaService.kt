package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.application.port.spi.UploadProgressSlot
import org.kotlogramme.cli.domain.AlbumItem
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sends media through the [MediaGateway].
 *
 * Malformed input is rejected here before the gateway is touched: a local upload is refused unless
 * the path exists and is a regular file, a stream is refused unless it carries a name and a
 * non-negative length, and a copy is refused unless the source message id is positive. The failures
 * are clear [IllegalArgumentException]s instead of a Telegram error. A caption is optional and may
 * be blank, and an empty stream is accepted. A video is validated like a file: the path must exist
 * and be a regular file. A reply-to message id is validated like the copy source: it must be
 * positive when present, and [silent] passes straight through.
 *
 * An upload opens the caller's [UploadProgressReporter] here, once the input is known to be good and
 * before any bytes move, and closes it once the send ends. Opening it in the service is what makes
 * the total available to a bar — the file's own length, or the length a stream declared — and
 * closing it in a `finally` is what leaves the terminal clean when the upload fails.
 */
class SendMediaService(private val gateway: MediaGateway) : SendMedia {
    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressReporter,
    ): Message {
        requireRegularFile(path)
        replyToMessageId?.let(::requirePositiveMessageId)
        return uploading(progress, Files.size(path)) { slot ->
            gateway.sendFile(reference, path, caption, asPhoto, replyToMessageId, silent, slot)
        }
    }

    override fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressReporter,
    ): Message {
        requireRegularFile(path)
        replyToMessageId?.let(::requirePositiveMessageId)
        return uploading(progress, Files.size(path)) { slot ->
            gateway.sendVideo(
                reference,
                path,
                caption,
                durationSeconds,
                width,
                height,
                replyToMessageId,
                silent,
                slot,
            )
        }
    }

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        size: Long,
        progress: UploadProgressReporter,
    ): Message {
        require(name.isNotBlank()) { "name must not be blank" }
        require(size >= 0) { "size must not be negative but was $size" }
        replyToMessageId?.let(::requirePositiveMessageId)
        return uploading(progress, size) { slot ->
            gateway.sendStream(reference, name, data, caption, asPhoto, replyToMessageId, silent, size, slot)
        }
    }

    override fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.sendUrl(reference, url, caption, asPhoto, replyToMessageId, silent)
    }

    override fun copyMedia(
        reference: String,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        requirePositiveMessageId(fromMessageId)
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.copyMedia(reference, fromMessageId, caption, replyToMessageId, silent)
    }

    override fun sendAlbum(reference: String, items: List<AlbumItem>): List<Message> {
        require(items.isNotEmpty()) { "an album needs at least one item" }
        require(items.size <= MAX_ALBUM_ITEMS) {
            "an album holds at most $MAX_ALBUM_ITEMS items but ${items.size} were given"
        }
        items.forEach { requireRegularFile(it.path) }
        return gateway.sendAlbum(reference, items)
    }

    private fun requireRegularFile(path: Path) {
        require(Files.exists(path)) { "file does not exist: $path" }
        require(Files.isRegularFile(path)) { "not a regular file: $path" }
    }

    /**
     * Opens a progress slot for an upload of [totalBytes] and closes it once [send] ends.
     *
     * The close is in a `finally`, so a failed upload erases its bar just as a successful one does.
     */
    private inline fun uploading(
        progress: UploadProgressReporter,
        totalBytes: Long,
        send: (UploadProgressSlot) -> Message,
    ): Message {
        val slot = progress.begin(totalBytes)
        return try {
            send(slot)
        } finally {
            slot.close()
        }
    }

    private fun requirePositiveMessageId(messageId: Int) {
        require(messageId > 0) { "message id must be positive but was $messageId" }
    }

    private companion object {
        /** Telegram's own limit for a grouped message. */
        const val MAX_ALBUM_ITEMS = 10
    }
}
