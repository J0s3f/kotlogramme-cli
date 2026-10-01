package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.spi.MediaGateway
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads a message's media through the [MediaGateway].
 *
 * The message id must be positive and the target's parent directory must already exist. The
 * directory is deliberately not created: a typo in one path component would otherwise be buried
 * under a silently created folder, so it fails with a clear [IllegalArgumentException]. A bare file
 * name is allowed and resolves against the working directory, which always has a parent.
 */
class DownloadMediaService(private val gateway: MediaGateway) : DownloadMedia {
    override fun download(reference: String, messageId: Int, target: Path): Path {
        require(messageId > 0) { "message id must be positive but was $messageId" }
        requireParentDirectory(target)
        return gateway.download(reference, messageId, target)
    }

    override fun fileName(reference: String, messageId: Int): String? {
        require(messageId > 0) { "message id must be positive but was $messageId" }
        return gateway.fileName(reference, messageId)
    }

    private fun requireParentDirectory(target: Path) {
        val parent = target.toAbsolutePath().parent ?: return
        require(Files.isDirectory(parent)) { "download directory does not exist: $parent" }
    }
}
