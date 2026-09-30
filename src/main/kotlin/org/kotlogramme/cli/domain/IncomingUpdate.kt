package org.kotlogramme.cli.domain

/** An update pushed by Telegram. */
sealed interface IncomingUpdate {
    /** A new message arrived; [chat] is `null` when the update carries no peer. */
    data class NewMessage(val chat: Chat?, val message: Message) : IncomingUpdate

    /** Any other update, identified by its facade kind. */
    data class Other(val kind: String) : IncomingUpdate
}
