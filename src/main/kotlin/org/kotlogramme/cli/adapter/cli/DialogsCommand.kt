package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderChats
import org.kotlogramme.cli.application.ListingCursor

/** Lists the account's conversations, newest first. */
class DialogsCommand : CliktCommand(name = "dialogs") {
    private val appContext by requireObject<AppContext>()

    private val limit by option("--limit", help = "How many dialogs to list").int().default(DEFAULT_LIMIT)
    private val after by option("--after", help = "The cursor to continue from, as the last page printed it")
    private val all by option(
        "--all",
        help = "List every dialog in one call, ignoring the cursor and the limit",
    ).flag()

    override fun run() {
        val chats = rejectInvalidInput { appContext.listDialogs().list(limit, after, all) }
        appContext.output.renderChats(chats)
        // Saved Messages is a synthetic anchor row, not a real dialog: it must not count toward
        // the page size or the next cursor, which is built from the last real dialog row.
        val dialogs = chats.filterNot { it.isSelf }
        if (dialogs.size >= limit && !all) {
            appContext.output.line("# next: --after ${ListingCursor.formatDialogs(dialogs.last())}")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
