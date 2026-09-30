package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages

/**
 * Searches messages.
 *
 * Without `--in` the search covers the whole account; with it, only the named chat. `--total` prints
 * just the number of matches, which may exceed the page `--limit` returns.
 */
class SearchCommand : CliktCommand(name = "search") {
    private val appContext by requireObject<AppContext>()

    private val query by argument("query", help = "The text to search for")
    private val inPeer by option("--in", help = "Search only this chat; global when omitted")
    private val limit by option("--limit", help = "How many matches to show").int().default(DEFAULT_LIMIT)
    private val total by option("--total", help = "Print only the number of matches").flag()

    override fun run() {
        val search = appContext.searchMessages()
        if (total) {
            val matches = rejectInvalidInput { search.total(inPeer, query) }
            appContext.output.line(matches.toString())
        } else {
        appContext.output.renderMessages(
            rejectInvalidInput { search.search(inPeer, query, limit) },
            appContext.messageStyler,
        )
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
