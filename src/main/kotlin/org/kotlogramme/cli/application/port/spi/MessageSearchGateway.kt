package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Message

/** Full-text message search, in one chat or across all of them. */
interface MessageSearchGateway {
    /** [reference] is `null` for a global search. */
    fun search(reference: String?, query: String, limit: Int): List<Message>

    /** The total number of matches, which may exceed what [search] returns. */
    fun total(reference: String?, query: String): Int
}
