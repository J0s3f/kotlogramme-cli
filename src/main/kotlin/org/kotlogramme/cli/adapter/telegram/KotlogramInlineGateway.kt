package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.InlineGateway
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.Message

/**
 * The [InlineGateway] backed by the kotlogramme facade.
 *
 * The bot and the optional chat context are resolved through the same [ChatReferenceResolver] the
 * rest of the client uses, so a `@username`, an invite link and a numeric id mean the same peer
 * here as they do elsewhere. The message a send produces is mapped through the existing
 * `MessageMapper`, so it renders exactly like any other message.
 */
internal class KotlogramInlineGateway(
    private val operations: FacadeInlineOperations,
    private val resolver: ChatReferenceResolver,
) : InlineGateway {
    override fun query(bot: String, query: String, reference: String?): InlineQuery =
        operations.query(
            bot = resolver.resolve(bot),
            query = query,
            peer = reference?.let(resolver::resolve),
        ).toInlineQuery()

    override fun send(reference: String, queryId: Long, resultId: String): Message? =
        operations.send(resolver.resolve(reference), queryId, resultId)?.toMessage()
}
