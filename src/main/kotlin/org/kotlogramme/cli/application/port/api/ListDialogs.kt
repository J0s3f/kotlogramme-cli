package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Chat

/** List the conversations the account is part of. */
interface ListDialogs {
    fun list(limit: Int): List<Chat>
}
