package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message

/** Full-text message search, in one chat or across all of them. */
interface MessageSearchGateway {
    /** [reference] is `null` for a global search. */
    fun search(reference: String?, query: String, limit: Int): List<Message>

    /** The total number of matches, which may exceed what [search] returns. */
    fun total(reference: String?, query: String): Int

    /** The messages of [reference] that carry media of [kind], found by the server-side filter. */
    fun files(reference: String, kind: MediaFileKind, limit: Int): List<Message>

    /** How many messages of [reference] carry media of [kind]. */
    fun fileTotal(reference: String, kind: MediaFileKind): Int
}
