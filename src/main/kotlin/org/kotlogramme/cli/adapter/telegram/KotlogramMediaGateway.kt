package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Path

/**
 * The [MediaGateway] backed by the kotlogramme facade.
 *
 * References are resolved through the same [ChatReferenceResolver] the rest of the stack uses, so a
 * `@username`, an invite link and a numeric id all mean the same chat here as they do elsewhere. A
 * copy re-sends the source message's media back to the same conversation, a stream is uploaded
 * first and then sent by handle, and the download reports the path the facade actually wrote. A
 * file name is read off the message itself, so it costs one message lookup and only when a caller
 * asks for it.
 */
internal class KotlogramMediaGateway(
    private val operations: FacadeMediaOperations,
    private val resolver: ChatReferenceResolver,
) : MediaGateway {
    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        operations.sendFile(resolver.resolve(reference), path, caption, asPhoto, replyToMessageId, silent).toMessage()

    override fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        operations.sendVideo(
            resolver.resolve(reference),
            path,
            caption,
            durationSeconds,
            width,
            height,
            replyToMessageId,
            silent,
        ).toMessage()


    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        val peer = resolver.resolve(reference)
        val uploaded = operations.uploadStream(data, name)
        return operations.sendUploaded(peer, uploaded, caption, asPhoto, replyToMessageId, silent).toMessage()
    }

    override fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        operations.sendUrl(resolver.resolve(reference), url, caption, asPhoto, replyToMessageId, silent).toMessage()

    override fun copyMedia(
        reference: String,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message =
        operations.copyMedia(resolver.resolve(reference), fromMessageId, caption, replyToMessageId, silent).toMessage()

    override fun download(reference: String, messageId: Int, target: Path): Path =
        Path.of(operations.download(resolver.resolve(reference), messageId, target).path)

    override fun fileName(reference: String, messageId: Int): String? =
        operations.message(resolver.resolve(reference), messageId)?.toMessage()?.media?.name
}
