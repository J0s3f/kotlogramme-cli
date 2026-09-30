package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.int

/** Pins one message in a chat. */
class PinCommand : CliktCommand(name = "pin") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message to pin").int()

    override fun run() {
        rejectInvalidInput { appContext.messageWriter().pin(peer, messageId) }
        appContext.output.line("Pinned message $messageId in $peer.")
    }
}

/** Removes a pinned message from a chat. */
class UnpinCommand : CliktCommand(name = "unpin") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message to unpin").int()

    override fun run() {
        rejectInvalidInput { appContext.messageWriter().unpin(peer, messageId) }
        appContext.output.line("Unpinned message $messageId in $peer.")
    }
}
