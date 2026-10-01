package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.kotlogramme.cli.adapter.cli.shell.JLineLineSource
import org.kotlogramme.cli.adapter.cli.shell.Shell
import org.kotlogramme.cli.adapter.cli.shell.ShellCompleter
import org.kotlogramme.cli.adapter.cli.shell.ShellUseCases
import java.nio.file.Files

/**
 * Starts the interactive shell.
 *
 * JLine supplies the terminal, the line editing, the completion and the history file, which lives
 * under the config directory. The shell itself is terminal-free and dispatches to the same use
 * cases as the one-shot commands.
 */
class ShellCommand : CliktCommand(name = "shell") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        Files.createDirectories(appContext.configDir)
        val terminal = TerminalBuilder.builder().system(true).build()
        val useCases = ShellUseCases(
            listDialogs = appContext::listDialogs,
            readHistory = appContext::readHistory,
            messageWriter = appContext::messageWriter,
            contacts = appContext::contacts,
            searchMessages = appContext::searchMessages,
            chatMembers = appContext::chatMembers,
            listFolders = appContext::listFolders,
            stickers = appContext::stickers,
            inline = appContext::inline,
            sendMedia = appContext::sendMedia,
            downloadMedia = appContext::downloadMedia,
        )
        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .appName(APP_NAME)
            .completer(ShellCompleter(peerReferences()))
            .variable(LineReader.HISTORY_FILE, appContext.configDir.resolve(HISTORY_FILE).toString())
            .build()
        try {
            Shell(JLineLineSource(reader), appContext.output, useCases, appContext.messageStyler).run()
        } finally {
            runCatching { reader.history.save() }
            terminal.close()
        }
    }

    private fun peerReferences(): () -> List<String> = {
        runCatching { appContext.listDialogs().list(COMPLETION_LIMIT).map { it.reference } }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val APP_NAME = "kotlogramme"
        const val HISTORY_FILE = "shell-history"
        const val COMPLETION_LIMIT = 100
    }
}
