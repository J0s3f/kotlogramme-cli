package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Message
import java.nio.file.Path

/** The media operations the facade exposes, in domain terms. */
interface MediaGateway {
    /** Uploads a local file and sends it, as a photo when [asPhoto] is set. */
    fun sendFile(reference: String, path: Path, caption: String, asPhoto: Boolean): Message

    /** Lets Telegram fetch a URL and send it as media. */
    fun sendUrl(reference: String, url: String, caption: String, asPhoto: Boolean): Message

    /** Re-sends the media of an existing message without a re-upload. */
    fun copyMedia(reference: String, fromMessageId: Int, caption: String): Message

    /** Downloads the media of a message to [target] and returns the path actually written. */
    fun download(reference: String, messageId: Int, target: Path): Path
}
