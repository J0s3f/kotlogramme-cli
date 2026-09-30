package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.spi.MessageGateway
import org.kotlogramme.cli.application.port.spi.UserGateway
import org.kotlogramme.cli.domain.Message

/**
 * Reads a page of a chat's history, rejecting a limit that would ask for nothing or less.
 *
 * The page's inline-bot origins are resolved in one batched [UserGateway] lookup of the distinct
 * `viaBotId`s, so the renderer can show `@username` instead of a bare number. Only the history path
 * pays for it: `listen`, search and the send commands read messages through their own gateways and
 * are untouched. The lookup is best-effort — if it fails, or an id does not resolve, the message is
 * returned as it was and the command still succeeds.
 */
class ReadHistoryService(
    private val gateway: MessageGateway,
    private val users: UserGateway,
) : ReadHistory {
    override fun read(reference: String, limit: Int, beforeMessageId: Int?): List<Message> {
        require(limit > 0) { "limit must be positive but was $limit" }
        return withViaBotUsernames(gateway.history(reference, limit, beforeMessageId))
    }

    private fun withViaBotUsernames(messages: List<Message>): List<Message> {
        val viaBotIds = messages.mapNotNull(Message::viaBotId).distinct()
        if (viaBotIds.isEmpty()) return messages

        val usernames = try {
            users.usernames(viaBotIds)
        } catch (failure: Exception) {
            emptyMap()
        }
        if (usernames.isEmpty()) return messages

        return messages.map { message ->
            val username = message.viaBotId?.let(usernames::get)
            if (username == null) message else message.copy(viaBotUsername = username)
        }
    }
}
