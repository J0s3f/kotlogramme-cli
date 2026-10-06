package org.kotlogramme.cli.adapter.format

import com.github.ajalt.mordant.terminal.Terminal

/**
 * The width of the terminal the process writes to, or `null` when it does not write to one.
 *
 * Only an interactive output has a width worth fitting a table to. A pipe or a file reports a default
 * size, and wrapping for that would corrupt what a script reads, so it reports `null` and the table
 * is left as wide as its content. The terminal is created on first use, and its size is read again
 * each time because the window can be resized between two commands of a shell.
 */
internal class TerminalColumns(private val open: () -> Terminal = ::Terminal) {
    private val terminal by lazy(open)

    fun get(): Int? = runCatching {
        if (terminal.terminalInfo.outputInteractive) terminal.updateSize().width else null
    }.getOrNull()
}
