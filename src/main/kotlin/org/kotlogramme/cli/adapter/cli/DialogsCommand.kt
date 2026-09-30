package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderChats

/** Lists the account's conversations, newest first. */
class DialogsCommand : CliktCommand(name = "dialogs") {
    private val appContext by requireObject<AppContext>()

    private val limit by option("--limit", help = "How many dialogs to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderChats(appContext.listDialogs().list(limit))
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
