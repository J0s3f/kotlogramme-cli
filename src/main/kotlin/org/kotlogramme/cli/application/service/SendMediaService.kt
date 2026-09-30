package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sends media through the [MediaGateway].
 *
 * Malformed input is rejected here before the gateway is touched: a local upload is refused unless
 * the path exists and is a regular file, and a copy is refused unless the source message id is
 * positive. The failures are clear [IllegalArgumentException]s instead of a Telegram error. A
 * caption is optional and may be blank.
 */
class SendMediaService(private val gateway: MediaGateway) : SendMedia {
    override fun sendFile(reference: String, path: Path, caption: String, asPhoto: Boolean): Message {
        requireRegularFile(path)
        return gateway.sendFile(reference, path, caption, asPhoto)
    }

    override fun sendUrl(reference: String, url: String, caption: String, asPhoto: Boolean): Message =
        gateway.sendUrl(reference, url, caption, asPhoto)

    override fun copyMedia(reference: String, fromMessageId: Int, caption: String): Message {
        requirePositiveMessageId(fromMessageId)
        return gateway.copyMedia(reference, fromMessageId, caption)
    }

    private fun requireRegularFile(path: Path) {
        require(Files.exists(path)) { "file does not exist: $path" }
        require(Files.isRegularFile(path)) { "not a regular file: $path" }
    }

    private fun requirePositiveMessageId(messageId: Int) {
        require(messageId > 0) { "message id must be positive but was $messageId" }
    }
}
