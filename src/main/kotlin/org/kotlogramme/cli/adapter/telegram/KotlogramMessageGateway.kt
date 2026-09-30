package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.MessageGateway
import org.kotlogramme.cli.domain.Message

/**
 * The [MessageGateway] backed by the kotlogramme facade.
 *
 * The facade returns a page newest first, while the port promises the page oldest first, so the
 * mapped page is reversed. The reference is resolved through the same seam the chat gateway uses.
 */
internal class KotlogramMessageGateway(
    private val chatOperations: FacadeChatOperations,
    private val messageOperations: FacadeMessageOperations,
) : MessageGateway {
    private val resolver = ChatReferenceResolver(chatOperations)

    override fun history(reference: String, limit: Int, beforeMessageId: Int?): List<Message> =
        messageOperations.history(resolver.resolve(reference), limit, beforeMessageId)
            .map { it.toMessage() }
            .reversed()
}
