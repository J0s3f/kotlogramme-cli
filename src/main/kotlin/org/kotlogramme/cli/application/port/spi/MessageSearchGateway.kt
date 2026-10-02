package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/** Full-text message search, in one chat or across all of them. */
interface MessageSearchGateway {
    /**
     * Searches [limit] matches, newest first.
     *
     * [reference] is `null` for a global search. [cursor] continues from a previous page, as the
     * `# next:` line printed it.
     */
    fun search(reference: String?, query: String, limit: Int, cursor: String? = null): List<Message>

    /** The total number of matches, which may exceed what [search] returns. */
    fun total(reference: String?, query: String): Int

    /**
     * The messages of [reference] that carry media of [kind], found by the server-side filter.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it.
     */
    fun files(reference: String, kind: MediaFileKind, limit: Int, cursor: String? = null): List<Message>

    /** How many messages of [reference] carry media of [kind]. */
    fun fileTotal(reference: String, kind: MediaFileKind): Int
}
