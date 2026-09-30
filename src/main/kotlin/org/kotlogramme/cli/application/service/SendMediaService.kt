package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sends media through the [MediaGateway].
 *
 * Malformed input is rejected here before the gateway is touched: a local upload is refused unless
 * the path exists and is a regular file, a stream is refused unless it carries a name, and a copy is
 * refused unless the source message id is positive. The failures are clear
 * [IllegalArgumentException]s instead of a Telegram error. A caption is optional and may be blank,
 * and an empty stream is accepted. A video is validated like a file: the path must exist and be a
 * regular file. A reply-to message id is validated like the copy source: it must be positive when
 * present, and [silent] passes straight through.
 */
class SendMediaService(private val gateway: MediaGateway) : SendMedia {
    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        requireRegularFile(path)
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.sendFile(reference, path, caption, asPhoto, replyToMessageId, silent)
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
    ): Message {
        requireRegularFile(path)
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.sendVideo(reference, path, caption, durationSeconds, width, height, replyToMessageId, silent)
    }

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        require(name.isNotBlank()) { "name must not be blank" }
        replyToMessageId?.let(::requirePositiveMessageId)
        return gateway.sendStream(reference, name, data, caption, asPhoto, replyToMessageId, silent)
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

    private fun requireRegularFile(path: Path) {
        require(Files.exists(path)) { "file does not exist: $path" }
        require(Files.isRegularFile(path)) { "not a regular file: $path" }
    }

    private fun requirePositiveMessageId(messageId: Int) {
        require(messageId > 0) { "message id must be positive but was $messageId" }
    }
}
