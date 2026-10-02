package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Chat

/** List the conversations the account is part of. */
interface ListDialogs {
    /**
     * Lists [limit] dialogs, newest first.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it; [all] walks the
     * whole set in one call and wins over both [cursor] and [limit].
     */
    fun list(limit: Int, cursor: String? = null, all: Boolean = false): List<Chat>
}
