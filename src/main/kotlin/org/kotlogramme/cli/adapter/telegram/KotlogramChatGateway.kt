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

    override fun dialogs(limit: Int): List<Chat> {
        val savedMessages = operations.resolveSelf().toSavedMessagesChat()
        val dialogs = operations.dialogs(limit)
            .filterNot { it.isFolder }
            .map { it.toChat(now()) }
        // Saved Messages is an extra anchor row, not one of the requested dialogs: `limit` still
        // buys that many real conversations, so `list 20` shows 21 rows. Telegram does not return
        // the self peer in `messages.getDialogs`, but a defensive filter keeps it from appearing
        // twice if that ever changes.
        return listOf(savedMessages) + dialogs.filterNot { it.id == savedMessages.id }
    }

    override fun resolve(reference: String): Chat = resolver.resolve(reference).toChat()
}
