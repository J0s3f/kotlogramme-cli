package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.nio.file.Path

/**
 * The [MediaGateway] backed by the kotlogramme facade.
 *
 * References are resolved through the same [ChatReferenceResolver] the rest of the stack uses, so a
 * `@username`, an invite link and a numeric id all mean the same chat here as they do elsewhere. A
 * copy re-sends the source message's media back to the same conversation, and the download reports
 * the path the facade actually wrote.
 */
internal class KotlogramMediaGateway(
    private val operations: FacadeMediaOperations,
    private val resolver: ChatReferenceResolver,
) : MediaGateway {
    override fun sendFile(reference: String, path: Path, caption: String, asPhoto: Boolean): Message =
        operations.sendFile(resolver.resolve(reference), path, caption, asPhoto).toMessage()

    override fun sendUrl(reference: String, url: String, caption: String, asPhoto: Boolean): Message =
        operations.sendUrl(resolver.resolve(reference), url, caption, asPhoto).toMessage()

    override fun copyMedia(reference: String, fromMessageId: Int, caption: String): Message =
        operations.copyMedia(resolver.resolve(reference), fromMessageId, caption).toMessage()

    override fun download(reference: String, messageId: Int, target: Path): Path =
        Path.of(operations.download(resolver.resolve(reference), messageId, target).path)
}
