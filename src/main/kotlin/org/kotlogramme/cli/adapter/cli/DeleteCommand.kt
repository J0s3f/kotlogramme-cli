package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.int

/** Deletes one or more messages. */
class DeleteCommand : CliktCommand(name = "delete") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageIds by argument("messageId", help = "One or more message ids").int().multiple(required = true)

    override fun run() {
        val deleted = rejectInvalidInput { appContext.messageWriter().delete(peer, messageIds) }
        appContext.output.line("Deleted $deleted message(s).")
    }
}
