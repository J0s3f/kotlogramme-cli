package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message

/** Search messages, in one chat or globally. */
interface SearchMessages {
    /** [reference] is `null` for a global search. */
    fun search(reference: String?, query: String, limit: Int): List<Message>

    fun total(reference: String?, query: String): Int
}
