package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderInlineResults
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.InlineResult
import org.kotlogramme.cli.domain.Message

/**
 * Asks an inline bot and optionally sends one of its results.
 *
 * The results are always printed, numbered, so `--send` can name one; the count is what
 * [sendChosenResult] checks before a query id is used.
 *
 * Telegram expires the query id a send needs, so querying and sending are one command: the answer
 * is both printed and, with `--send` and `--to`, posted from the same call.
 */
class InlineCommand : CliktCommand(name = "inline") {
    private val appContext by requireObject<AppContext>()

    private val bot by argument("bot", help = "The inline bot: @username")
    private val query by argument("query", help = "The text to ask the bot")
    private val inChat by option("--in", help = "The chat the query is asked in")
    private val sendIndex by option("--send", help = "Send the result at this index").int()
    private val to by option("--to", help = "The chat to send the chosen result to")

    override fun run() {
        val answer = rejectInvalidInput { appContext.inline().query(bot, query, inChat) }
        appContext.output.renderInlineResults(answer)
        sendChosenResult(answer).let { messages ->
            if (messages.isNotEmpty()) appContext.output.renderMessages(messages)
        }
    }

    private fun sendChosenResult(query: InlineQuery): List<Message> {
        val index = sendIndex ?: return emptyList()
        val destination = to ?: throw UsageError("--send needs --to: name the chat to send the result to.")
        val result = requireResult(query, index)
        val sent = rejectInvalidInput { appContext.inline().send(destination, query.queryId, result.id) }
            ?: throw UsageError("The bot did not report a message for result $index.")
        return listOf(sent)
    }

    private fun requireResult(query: InlineQuery, index: Int): InlineResult =
        query.results.getOrNull(index)
            ?: throw UsageError("No result at index $index: the bot returned ${query.results.size}.")
}
