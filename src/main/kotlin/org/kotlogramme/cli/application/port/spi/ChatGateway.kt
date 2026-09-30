package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Chat

/** The chat and dialog operations the facade exposes, in domain terms. */
interface ChatGateway {
    fun dialogs(limit: Int): List<Chat>

    /** Resolves a username, numeric id or invite link into the [Chat] it names. */
    fun resolve(reference: String): Chat
}
