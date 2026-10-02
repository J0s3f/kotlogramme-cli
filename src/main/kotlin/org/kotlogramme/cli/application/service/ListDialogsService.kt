package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.spi.ChatGateway
import org.kotlogramme.cli.domain.Chat

/** Lists the account's conversations, rejecting a limit that would ask for nothing or less. */
class ListDialogsService(private val gateway: ChatGateway) : ListDialogs {
    override fun list(limit: Int, cursor: String?, all: Boolean): List<Chat> {
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.dialogs(limit, cursor, all)
    }
}
