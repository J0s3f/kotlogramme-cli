package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.domain.AlbumItem
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Path

/**
 * Send media: a local file, a stream, a URL Telegram fetches, or a copy of an existing message's media.
 *
 * Every send can quote a message with [replyToMessageId] and go out without a notification when
 * [silent] is set.
 *
 * Every send that puts bytes on the wire takes a [progress] reporter, which is how a caller watches
 * an upload that blocks: [UploadProgressReporter.SILENT] is the reporter for a run that shows no bar.
 */
interface SendMedia {
    fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressReporter,
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
        progress: UploadProgressReporter,
    ): Message

    /**
     * Sends a stream of exactly [size] bytes, which Telegram must be told before the first part goes
     * out, so a caller that does not know the length has to measure it first.
     */
    fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        size: Long,
        progress: UploadProgressReporter,
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

    /**
     * Sends one to ten files as a single grouped message.
     *
     * The facade's album carries no reply-to or silent flag, so neither is offered here; the
     * returned list is the messages Telegram created, in order.
     */
    fun sendAlbum(reference: String, items: List<AlbumItem>): List<Message>
}
