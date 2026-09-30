package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramPeer

/**
 * Turns a user-supplied reference into a facade peer.
 *
 * Three shapes are understood: a `@username`, a Telegram invite or public link, and a numeric
 * dialog id. Anything else is rejected with a message that says what was expected rather than
 * guessed at.
 */
internal class ChatReferenceResolver(private val operations: FacadeChatOperations) {
    fun resolve(reference: String): TelegramPeer {
        val trimmed = reference.trim()
        return when {
            trimmed.startsWith("@") -> operations.resolveUsername(trimmed.removePrefix("@"))
            trimmed.isTelegramLink() -> resolveLink(trimmed)
            trimmed.toLongOrNull() != null -> resolveId(trimmed.toLong())
            else -> throw IllegalArgumentException(unknownReference(trimmed))
        }
    }

    private fun resolveLink(link: String): TelegramPeer {
        val hash = operations.parseInviteLink(link)
        if (hash != null) {
            return operations.importChatInvite(link)
                ?: throw IllegalArgumentException("Could not join the invite link $link")
        }
        val username = link.publicLinkUsername()
            ?: throw IllegalArgumentException("Not a Telegram username or invite link: $link")
        return operations.resolveUsername(username)
    }

    private fun resolveId(id: Long): TelegramPeer =
        directLookup(id)
            ?: dialogLookup(id)
            ?: throw IllegalArgumentException(
                "No chat with id $id: this session knows no peer with that id. " +
                    "List the dialogs to see the known chats, or use @username.",
            )

    /**
     * The direct lookup, and the only one a bot can use: `messages.getDialogs` answers
     * BOT_METHOD_INVALID for a bot session, so a numeric id has to resolve from what the session
     * already knows.
     */
    private fun directLookup(id: Long): TelegramPeer? =
        runCatching { operations.resolvePeer(id) }.getOrNull()

    /** The dialog listing, which can name a peer the session has a dialog with but no handle for. */
    private fun dialogLookup(id: Long): TelegramPeer? =
        runCatching { operations.dialogs(DIALOG_LOOKUP_LIMIT) }
            .getOrNull()
            ?.firstOrNull { !it.isFolder && it.peer.id == id }
            ?.peer

    private fun unknownReference(reference: String): String =
        "Cannot resolve '$reference': expected @username, a numeric id, or a Telegram invite link"

    private fun String.isTelegramLink(): Boolean = startsWith("http://") || startsWith("https://")

    private fun String.publicLinkUsername(): String? {
        val rest = substringAfter("://", missingDelimiterValue = "")
        if (rest.isEmpty()) {
            return null
        }
        val path = rest.substringBefore('?').substringBefore('#')
        val authority = path.substringBefore('/')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host !in TELEGRAM_HOSTS) {
            return null
        }
        val segment = path.substringAfter('/', missingDelimiterValue = "").substringBefore('/')
        return segment.takeIf { it.isNotEmpty() && !it.startsWith("+") && !it.startsWith("joinchat") }
    }

    private companion object {
        /** How many dialogs a numeric-id lookup scans before giving up. */
        const val DIALOG_LOOKUP_LIMIT = 200

        /** The hosts a Telegram link can use, matching the facade's own invite-link parser. */
        val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "telegram.dog")
    }
}
