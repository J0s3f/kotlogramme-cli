package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.format.renderChats
import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.Message

/** The greeting shown once, so a first-time user knows `help` exists. */
internal const val SHELL_WELCOME = "kotlogramme shell - type `help` for commands, `quit` to exit."

/**
 * The interactive REPL.
 *
 * It reads lines from a [LineSource], parses them, dispatches to the same inbound ports as the
 * one-shot commands and renders through [Output]. Every terminal interaction goes through the
 * [LineSource], so a test can drive the whole shell with a scripted list of lines and fake ports.
 *
 * The shell remembers the chat that was opened and the last [TRANSCRIPT_LIMIT] messages it showed,
 * so the prompt carries the current conversation and `read` shows context even after a send.
 */
class Shell(
    private val lines: LineSource,
    private val output: Output,
    private val useCases: ShellUseCases,
) {
    private var currentChat: Chat? = null
    private val transcript = ArrayDeque<Message>()

    /** Runs until `quit`, `exit` or end of input. */
    fun run() {
        output.line(SHELL_WELCOME)
        while (true) {
            val input = lines.read(prompt())?.trim() ?: return
            if (input.isEmpty()) continue
            val name = input.substringBefore(' ').lowercase()
            if (name == QUIT || name == EXIT) return
            dispatch(name, input.substringAfter(' ', ""))
        }
    }

    private fun prompt(): String = currentChat?.let { "[${it.title}]> " } ?: NO_CHAT_PROMPT

    private fun dispatch(name: String, rest: String) {
        val arguments = rest.split(' ').filter(String::isNotEmpty)
        try {
            when (name) {
                "help" -> printHelp()
                "dialogs", "list" -> listDialogs()
                "open" -> open(arguments)
                "read" -> read(arguments)
                "send" -> send(arguments, replyToMessageId = null)
                "reply" -> reply(arguments)
                "contacts" -> listContacts()
                "search" -> search(arguments)
                else -> output.line("Unknown command: $name. Type `help` for the available commands.")
            }
        } catch (error: Exception) {
            output.line(error.message ?: error.toString())
        }
    }

    private fun printHelp() {
        HELP.forEach(output::line)
    }

    private fun listDialogs() {
        output.renderChats(useCases.listDialogs().list(DIALOG_LIMIT))
    }

    private fun open(arguments: List<String>) {
        val peer = arguments.firstOrNull()
        if (peer == null) {
            output.line("Usage: open <peer>")
            return
        }
        val chat = resolve(peer)
        if (chat == null) {
            output.line("No chat matches '$peer'.")
            return
        }
        currentChat = chat
        transcript.clear()
        output.line("Opened ${chat.title}.")
    }

    private fun read(arguments: List<String>) {
        val chat = requireChat() ?: return
        val limit = readLimit(arguments) ?: return
        remember(useCases.readHistory().read(chat.reference, limit, beforeMessageId = null))
        output.renderMessages(orderedTranscript())
    }

    private fun send(arguments: List<String>, replyToMessageId: Int?) {
        val chat = requireChat() ?: return
        val text = arguments.joinToString(" ").trim()
        if (text.isEmpty()) {
            output.line(if (replyToMessageId == null) "Usage: send <text...>" else "Usage: reply <id> <text...>")
            return
        }
        val sent = useCases.messageWriter().sendText(chat.reference, text, replyToMessageId, silent = false)
        remember(listOf(sent))
        output.renderMessages(listOf(sent))
    }

    private fun reply(arguments: List<String>) {
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        if (messageId == null) {
            output.line("Usage: reply <id> <text...>")
            return
        }
        send(arguments.drop(1), replyToMessageId = messageId)
    }

    private fun listContacts() {
        output.renderContacts(useCases.contacts().list(CONTACT_LIMIT))
    }

    private fun search(arguments: List<String>) {
        val query = arguments.joinToString(" ").trim()
        if (query.isEmpty()) {
            output.line("Usage: search <query>")
            return
        }
        val scope = currentChat?.reference
        output.renderMessages(useCases.searchMessages().search(scope, query, TRANSCRIPT_LIMIT))
    }

    private fun resolve(peer: String): Chat? {
        val normalized = peer.removePrefix("@").lowercase()
        return useCases.listDialogs().list(DIALOG_LIMIT).firstOrNull { chat ->
            chat.id.toString() == peer ||
                chat.reference.equals(peer, ignoreCase = true) ||
                chat.username?.lowercase() == normalized ||
                chat.title.equals(peer, ignoreCase = true)
        }
    }

    private fun requireChat(): Chat? {
        if (currentChat == null) output.line("No chat open. Use `open <peer>` first.")
        return currentChat
    }

    private fun readLimit(arguments: List<String>): Int? {
        if (arguments.isEmpty()) return TRANSCRIPT_LIMIT
        val index = arguments.indexOf(LIMIT_OPTION)
        val limit = arguments.getOrNull(index + 1)?.toIntOrNull()
        if (index < 0 || limit == null || limit <= 0) {
            output.line("Usage: read [--limit N]")
            return null
        }
        return limit
    }

    /** Keeps [messages] as the most recent context, replacing any earlier copy by id. */
    private fun remember(messages: List<Message>) {
        for (message in messages) {
            transcript.removeAll { it.id == message.id }
            transcript.addLast(message)
        }
        while (transcript.size > TRANSCRIPT_LIMIT) transcript.removeFirst()
    }

    /** The transcript oldest first, the way a conversation reads. */
    private fun orderedTranscript(): List<Message> = transcript.sortedBy(Message::id)

    private companion object {
        const val QUIT = "quit"
        const val EXIT = "exit"
        const val LIMIT_OPTION = "--limit"
        const val DIALOG_LIMIT = 100
        const val CONTACT_LIMIT = 50

        /** How many messages of the current chat the shell keeps in view. */
        const val TRANSCRIPT_LIMIT = 20

        const val NO_CHAT_PROMPT = "> "

        val HELP = listOf(
            "help                 show this help",
            "dialogs | list       list conversations",
            "open <peer>          open a chat",
            "read [--limit N]     read the current chat",
            "send <text...>       send a message",
            "reply <id> <text...> reply to a message",
            "contacts             list contacts",
            "search <query>       search the current chat, or everywhere",
            "quit | exit          leave the shell",
        )
    }
}
