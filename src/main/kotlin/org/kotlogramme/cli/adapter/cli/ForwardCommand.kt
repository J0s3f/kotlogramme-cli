package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages

/** Forwards one or more messages to another chat. */
class ForwardCommand : CliktCommand(name = "forward") {
    private val appContext by requireObject<AppContext>()

    private val fromPeer by argument("fromPeer", help = "The chat the messages come from")
    private val messageIds by argument("messageId", help = "One or more message ids").int().multiple(required = true)
    private val toPeer by option("--to", help = "The chat to forward to").required()

    override fun run() {
        val forwarded = rejectInvalidInput { appContext.messageWriter().forward(fromPeer, messageIds, toPeer) }
        appContext.output.renderMessages(forwarded)
    }
}
