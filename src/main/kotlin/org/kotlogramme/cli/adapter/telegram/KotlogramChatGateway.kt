package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.ChatGateway
import org.kotlogramme.cli.domain.Chat
import java.time.Instant

/**
 * The [ChatGateway] backed by the kotlogramme facade.
 *
 * [now] is injected so the mute comparison in the mapper is deterministic in a test; production
 * uses the wall clock.
 */
internal class KotlogramChatGateway(
    private val operations: FacadeChatOperations,
    private val now: () -> Instant = Instant::now,
) : ChatGateway {
    private val resolver = ChatReferenceResolver(operations)

    override fun dialogs(limit: Int): List<Chat> =
        operations.dialogs(limit)
            .filterNot { it.isFolder }
            .map { it.toChat(now()) }

    override fun resolve(reference: String): Chat = resolver.resolve(reference).toChat()
}
