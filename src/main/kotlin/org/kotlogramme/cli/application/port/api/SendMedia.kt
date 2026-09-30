package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message
import java.nio.file.Path

/** Send media: a local file, a URL Telegram fetches, or a copy of an existing message's media. */
interface SendMedia {
    fun sendFile(reference: String, path: Path, caption: String, asPhoto: Boolean): Message

    fun sendUrl(reference: String, url: String, caption: String, asPhoto: Boolean): Message

    fun copyMedia(reference: String, fromMessageId: Int, caption: String): Message
}
