package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Chat

/** The chat and dialog operations the facade exposes, in domain terms. */
interface ChatGateway {
    /**
     * Lists [limit] dialogs, newest first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun dialogs(limit: Int, cursor: String? = null, all: Boolean = false): List<Chat>

    /** Resolves a username, numeric id or invite link into the [Chat] it names. */
    fun resolve(reference: String): Chat
}
