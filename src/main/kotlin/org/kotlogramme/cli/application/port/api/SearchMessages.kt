package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/** Search messages, in one chat or globally. */
interface SearchMessages {
    /**
     * Searches [limit] matches, newest first.
     *
     * [reference] is `null` for a global search. [cursor] continues from a previous page, as the
     * `# next:` line printed it.
     */
    fun search(reference: String?, query: String, limit: Int, cursor: String? = null): List<Message>

    fun total(reference: String?, query: String): Int

    /**
     * The files of a chat that carry media of [kind], newest first.
     *
     * The kind is filtered on Telegram's side, so the listing reaches the whole chat rather than the
     * part of it a history scan has read. [cursor] continues from a previous page, as the `# next:`
     * line printed it.
     */
    fun files(reference: String, kind: MediaFileKind, limit: Int, cursor: String? = null): List<Message>

    /** How many files of [kind] the chat holds, which may exceed what [files] returns. */
    fun fileTotal(reference: String, kind: MediaFileKind): Int
}
