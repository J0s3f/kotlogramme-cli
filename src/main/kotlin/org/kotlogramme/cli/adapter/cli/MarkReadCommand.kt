package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument

/** Marks every message in a chat as read. */
class MarkReadCommand : CliktCommand(name = "mark-read") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.messageWriter().markRead(peer) }
        appContext.output.line("Marked $peer as read.")
    }
}
