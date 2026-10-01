package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.application.port.spi.UploadProgressSlot
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Path

/**
 * The media operations the facade exposes, in domain terms.
 *
 * An upload takes the [progress] slot the caller opened for it, and reports into it: the gateway
 * attaches a counter and closes nothing, because the slot's lifetime belongs to the caller.
 */
interface MediaGateway {
    /** Uploads a local file and sends it, as a photo when [asPhoto] is set. */
    fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressSlot,
    ): Message

    /**
     * Uploads a local file and sends it as a streamable video.
     *
     * [durationSeconds], [width] and [height] describe the video so Telegram can play it in place
     * rather than offering it as a download; any of them may be null.
     */
    fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressSlot,
    ): Message

    /** Uploads [size] bytes of [data] under [name] and sends it, as a photo when [asPhoto] is set. */
    fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        size: Long,
        progress: UploadProgressSlot,
    ): Message

    /** Lets Telegram fetch a URL and send it as media. */
    fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Re-sends the media of an existing message without a re-upload. */
    fun copyMedia(reference: String, fromMessageId: Int, caption: String, replyToMessageId: Int?, silent: Boolean): Message

    /** Downloads the media of a message to [target] and returns the path actually written. */
    fun download(reference: String, messageId: Int, target: Path): Path

    /** The file name a message's media carries, or `null` when it has none or does not resolve. */
    fun fileName(reference: String, messageId: Int): String?
}
