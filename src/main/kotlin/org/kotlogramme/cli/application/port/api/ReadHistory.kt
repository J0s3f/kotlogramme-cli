package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Message

/** Read a chat's history, newest page first. */
interface ReadHistory {
    fun read(reference: String, limit: Int, beforeMessageId: Int?): List<Message>
}
