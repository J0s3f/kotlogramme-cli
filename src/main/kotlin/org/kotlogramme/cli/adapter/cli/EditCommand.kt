package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages

/** Replaces the text of one message. */
class EditCommand : CliktCommand(name = "edit") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The id of the message to edit").int()
    private val text by argument("text", help = "The new text").multiple(required = true)

    override fun run() {
        val edited = rejectInvalidInput {
            appContext.messageWriter().edit(peer, messageId, text.joinToString(" "))
        }
        appContext.output.renderMessages(listOf(edited))
    }
}
