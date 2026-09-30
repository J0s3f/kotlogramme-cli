package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Path

/**
 * Send media: a local file, a stream, a URL Telegram fetches, or a copy of an existing message's media.
 *
 * Every send can quote a message with [replyToMessageId] and go out without a notification when
 * [silent] is set.
 */
interface SendMedia {
    fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Sends a local file as a streamable video, with the metadata Telegram plays it with. */
    fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    fun copyMedia(reference: String, fromMessageId: Int, caption: String, replyToMessageId: Int?, silent: Boolean): Message
}
