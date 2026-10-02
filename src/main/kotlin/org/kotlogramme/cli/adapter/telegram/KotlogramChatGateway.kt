package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.ListingCursor
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

    override fun dialogs(limit: Int, cursor: String?, all: Boolean): List<Chat> {
        val offset = cursor?.let(ListingCursor::parseDialogs)
        val dialogs = operations.dialogs(
            limit = limit,
            offsetPeer = offset?.peerId,
            offsetId = offset?.topMessageId,
            offsetDate = offset?.dateMillis,
            all = all,
        )
            .filterNot { it.isFolder }
            .map { it.toChat(now()) }
        // Saved Messages is a synthetic anchor row, not a dialog Telegram returns: it belongs on
        // the first page only, where a user looks for it. A subsequent page has no such row, and
        // `--all` is the first page followed by the rest, so it appears exactly once at the top.
        if (cursor != null && !all) return dialogs
        val savedMessages = operations.resolveSelf().toSavedMessagesChat()
        return listOf(savedMessages) + dialogs.filterNot { it.id == savedMessages.id }
    }

    override fun resolve(reference: String): Chat = resolver.resolve(reference).toChat()
}
