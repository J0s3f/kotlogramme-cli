package org.kotlogramme.cli.adapter.cli.shell

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * Completes the shell: command names in the first word and peer references after `open`.
 *
 * Peers are supplied lazily because listing them signs in; the supplier is expected to swallow a
 * failure (no credentials, no network) and return nothing rather than break completion. The word
 * matching lives in [candidatesFor] so it can be tested without a JLine reader.
 */
class ShellCompleter(private val peers: () -> List<String>) : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        candidatesFor(line.words(), line.wordIndex(), line.word()).forEach { candidates += Candidate(it) }
    }

    internal fun candidatesFor(words: List<String>, wordIndex: Int, word: String): List<String> {
        if (wordIndex == 0 && words.size <= 1) {
            return commands.filter { it.startsWith(word, ignoreCase = true) }
        }
        if (words.firstOrNull() == OPEN && wordIndex == 1) {
            return peers().filter { it.startsWith(word, ignoreCase = true) }
        }
        return emptyList()
    }

    private companion object {
        const val OPEN = "open"

        val commands = listOf(
            "help", "dialogs", "list", "open", "read",
            "send", "reply", "edit", "delete", "forward",
            "pin", "unpin", "react", "unreact", "mark-read", "invite", "kick",
            "contacts", "search", "files", "download-media", "send-media-url", "copy-media",
            "stickers", "sticker-set", "send-sticker", "inline", "members", "folders",
            "quit", "exit",
        )
    }

    /** The command verbs the completer offers; a test compares them with what `dispatch` accepts. */
    internal fun completeCommands(): List<String> = commands
}
