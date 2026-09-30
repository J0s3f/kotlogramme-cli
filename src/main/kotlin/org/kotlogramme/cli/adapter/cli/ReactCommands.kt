package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.int

/** Adds a reaction to one message. */
class ReactCommand : CliktCommand(name = "react") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message to react to").int()
    private val emoji by argument("emoji", help = "The reaction emoji, or a U+1F44D code point")

    override fun run() {
        val reaction = decodeEmojiArgument(emoji)
        rejectInvalidInput { appContext.messageWriter().react(peer, messageId, reaction) }
        appContext.output.line("Reacted to message $messageId in $peer.")
    }
}

/** Removes the reaction from one message. */
class UnreactCommand : CliktCommand(name = "unreact") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message to clear").int()

    override fun run() {
        rejectInvalidInput { appContext.messageWriter().removeReaction(peer, messageId) }
        appContext.output.line("Removed the reaction from message $messageId in $peer.")
    }
}
