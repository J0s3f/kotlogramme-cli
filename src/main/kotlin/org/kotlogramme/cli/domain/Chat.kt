package org.kotlogramme.cli.domain

import java.time.Instant

/** The kind of conversation a [Chat] is. */
enum class ChatKind {
    PRIVATE,
    GROUP,
    SUPERGROUP,
    CHANNEL,
    ;

    companion object {
        /** Maps the facade's peer kind, tolerating case and the `megagroup` spelling. */
        fun fromFacade(kind: String, megagroup: Boolean?): ChatKind = when (kind.lowercase()) {
            "user", "private" -> PRIVATE
            "chat", "group" -> GROUP
            "channel" -> if (megagroup == true) SUPERGROUP else CHANNEL
            else -> PRIVATE
        }
    }
}

/**
 * A conversation as the terminal client shows it in the dialog list.
 *
 * [reference] is what the gateway resolves back to a Telegram peer: the username when there is one,
 * otherwise the numeric id. The domain never holds a facade peer handle.
 */
data class Chat(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val username: String?,
    val lastMessagePreview: String?,
    val lastMessageAt: Instant?,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
) {
    val reference: String
        get() = username?.let { "@$it" } ?: id.toString()
}
