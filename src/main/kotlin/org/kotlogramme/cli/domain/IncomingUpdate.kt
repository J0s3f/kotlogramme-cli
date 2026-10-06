package org.kotlogramme.cli.domain

/** An update pushed by Telegram. */
sealed interface IncomingUpdate {
    /** A new message arrived; [chat] is `null` when the update carries no peer. */
    data class NewMessage(val chat: Chat?, val message: Message) : IncomingUpdate

    /**
     * Any other update, identified by its facade kind or, for an update the facade does not type, by
     * the name of the Telegram update it carries. [data] is its payload as JSON text, empty when
     * the facade attaches none. [userId] is the user the update is about, such as the one typing or
     * changing status, or `null` when the update concerns nobody in particular.
     */
    data class Other(val kind: String, val data: String = "", val userId: Long? = null) : IncomingUpdate
}
