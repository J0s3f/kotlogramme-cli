package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages

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

/**
 * Removes a pinned message from a chat, or every pinned message with `--all`.
 *
 * A message id and `--all` are mutually exclusive: one names a single pin, the other clears the
 * whole set, and silently preferring one over the other would unpin more than was asked for.
 */
class UnpinCommand : CliktCommand(name = "unpin") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message to unpin").int().optional()
    private val all by option("--all", help = "Unpin every message in the chat").flag()

    override fun run() {
        val id = messageId
        when {
            all && id != null ->
                throw UsageError("Give either a message id or --all, not both.")
            all -> {
                rejectInvalidInput { appContext.messageWriter().unpinAll(peer) }
                appContext.output.line("Unpinned every message in $peer.")
            }
            id == null ->
                throw UsageError("Give a message id to unpin, or --all to unpin every message.")
            else -> {
                rejectInvalidInput { appContext.messageWriter().unpin(peer, id) }
                appContext.output.line("Unpinned message $id in $peer.")
            }
        }
    }
}

/** Shows the message a chat has pinned, or says that it has none. */
class PinnedCommand : CliktCommand(name = "pinned") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")

    override fun run() {
        val pinned = rejectInvalidInput { appContext.messageWriter().pinnedMessage(peer) }
        if (pinned == null) {
            appContext.output.line("No pinned message in $peer.")
        } else {
            appContext.output.renderMessages(listOf(pinned), appContext.messageStyler)
        }
    }
}
