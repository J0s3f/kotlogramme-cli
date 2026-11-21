package org.kotlogramme.cli.domain

/** An update pushed by Telegram. */
sealed interface IncomingUpdate {
    /** A new message arrived; [chat] is `null` when the update carries no peer. */
    data class NewMessage(val chat: Chat?, val message: Message) : IncomingUpdate

    /**
     * A forum topic of the chat [chatId] changed. Telegram reports only what changed, so each of
     * [title], [iconEmojiId], [closed] and [hidden] is `null` when that part stayed as it was.
     */
    data class TopicEdited(
        val chatId: Long,
        val topicId: Int,
        val title: String? = null,
        val iconEmojiId: Long? = null,
        val closed: Boolean? = null,
        val hidden: Boolean? = null,
    ) : IncomingUpdate

    /**
     * Any other update, identified by its facade kind or, for an update the facade does not type, by
     * the name of the Telegram update it carries. [data] is its payload as JSON text, empty when
     * the facade attaches none. [userId] is the user the update is about, such as the one typing or
     * changing status, or `null` when the update concerns nobody in particular.
     */
    data class Other(val kind: String, val data: String = "", val userId: Long? = null) : IncomingUpdate
}
