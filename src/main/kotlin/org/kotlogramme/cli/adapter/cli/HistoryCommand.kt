package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages

/** Reads a page of a chat's history, oldest first, with paging through `--before`. */
class HistoryCommand : CliktCommand(name = "history") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val limit by option("--limit", help = "How many messages to read").int().default(DEFAULT_LIMIT)
    private val before by option("--before", help = "Only messages older than this id").int()

    override fun run() {
        appContext.output.renderMessages(appContext.readHistory().read(peer, limit, before))
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
