package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DownloadedMedia
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import com.github.badoualy.telegram.api.UploadedFile
import java.io.InputStream
import java.nio.file.Path

/**
 * The facade media calls the gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramMediaGateway] can be exercised without a live client. Each method
 * mirrors one facade operation; the facade models are mapped to the domain at the gateway boundary.
 */
internal interface FacadeMediaOperations {
    /** Uploads [path] to [peer] as a photo or a document, which is `mediaSend`. */
    fun sendFile(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Uploads [path] to [peer] as a streamable video, which is `mediaSend` with `MediaKind.VIDEO`. */
    fun sendVideo(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Uploads [data] under [name] and returns the handle a later send references. */
    fun uploadStream(data: InputStream, name: String): UploadedFile

    /** Sends the already-uploaded [file] to [peer], which is the `UploadedFile` overload of `mediaSend`. */
    fun sendUploaded(
        peer: TelegramPeer,
        file: UploadedFile,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Sends media Telegram fetches from [url], which is `mediaSendUrl`. */
    fun sendUrl(
        peer: TelegramPeer,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Re-sends the media of message [fromMessageId] in [peer], which is `mediaCopy`. */
    fun copyMedia(
        peer: TelegramPeer,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message

    /** Downloads the media of message [messageId] in [peer] into [target], which is `downloadMedia`. */
    fun download(peer: TelegramPeer, messageId: Int, target: Path): DownloadedMedia

    /** The message [messageId] of [peer], or `null` when it does not resolve, which is `getMessages`. */
    fun message(peer: TelegramPeer, messageId: Int): Message?
}
