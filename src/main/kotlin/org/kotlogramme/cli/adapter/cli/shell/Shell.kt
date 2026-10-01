package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.format.MessageStyler
import org.kotlogramme.cli.adapter.format.renderChats
import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.adapter.format.renderFolders
import org.kotlogramme.cli.adapter.format.renderInlineResults
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.format.renderParticipants
import org.kotlogramme.cli.adapter.format.renderStickerSet
import org.kotlogramme.cli.adapter.format.renderStickerSets
import org.kotlogramme.cli.adapter.cli.DEFAULT_FILE_KIND
import org.kotlogramme.cli.adapter.cli.downloadFileName
import org.kotlogramme.cli.adapter.cli.mediaFileKindOf
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.Message
import java.nio.file.Files
import java.nio.file.Path

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
    private val messageStyler: MessageStyler = MessageStyler.PLAIN,
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
                "files" -> listFiles(arguments)
                "download-media" -> downloadMedia(arguments)
                "stickers" -> listStickerSets()
                "sticker-set" -> showStickerSet(arguments)
                "send-sticker" -> sendSticker(arguments)
                "inline" -> inline(arguments)
                "members" -> listMembers(arguments)
                "folders" -> listFolders()
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
        val limit = pageLimit(arguments, READ_USAGE) ?: return
        remember(useCases.readHistory().read(chat.reference, limit, beforeMessageId = null))
        output.renderMessages(orderedTranscript(), messageStyler)
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
        output.renderMessages(listOf(sent), messageStyler)
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
        output.renderMessages(
            useCases.searchMessages().search(scope, query, TRANSCRIPT_LIMIT),
            messageStyler,
        )
    }

    private fun listStickerSets() {
        output.renderStickerSets(useCases.stickers().sets())
    }

    private fun showStickerSet(arguments: List<String>) {
        val set = arguments.firstOrNull()
        if (set == null) {
            output.line("Usage: sticker-set <set>")
            return
        }
        output.renderStickerSet(useCases.stickers().set(set))
    }

    /** Sends a sticker into the current chat, the one the prompt names. */
    private fun sendSticker(arguments: List<String>) {
        val chat = requireChat() ?: return
        val set = arguments.firstOrNull()
        val index = arguments.getOrNull(1)?.toIntOrNull()
        if (set == null || index == null) {
            output.line("Usage: send-sticker <set> <index>")
            return
        }
        val sent = useCases.stickers().send(chat.reference, set, index)
        remember(listOf(sent))
        output.renderMessages(listOf(sent), messageStyler)
    }

    /**
     * Asks an inline bot, and with `--send` posts the chosen result into the current chat.
     *
     * The current chat is both the context the query is asked in and the destination, so the
     * one-shot command's `--to` is unnecessary here.
     */
    private fun inline(arguments: List<String>) {
        val sendAt = arguments.indexOf(SEND_OPTION)
        val sendIndex = if (sendAt >= 0) arguments.getOrNull(sendAt + 1)?.toIntOrNull() else null
        if (sendAt >= 0 && sendIndex == null) {
            output.line("Usage: inline <bot> <query> [--send <index>]")
            return
        }
        val words = if (sendAt >= 0) {
            arguments.filterIndexed { index, _ -> index != sendAt && index != sendAt + 1 }
        } else {
            arguments
        }
        val bot = words.firstOrNull()
        val query = words.drop(1).joinToString(" ").trim()
        if (bot == null || query.isEmpty()) {
            output.line("Usage: inline <bot> <query> [--send <index>]")
            return
        }
        val destination = if (sendIndex != null) requireChat() ?: return else currentChat
        val answer = useCases.inline().query(bot, query, destination?.reference)
        output.renderInlineResults(answer)
        if (sendIndex == null) return
        val result = answer.results.getOrNull(sendIndex)
        if (result == null) {
            output.line("No result at index $sendIndex: the bot returned ${answer.results.size}.")
            return
        }
        val sent = useCases.inline().send(requireNotNull(destination).reference, answer.queryId, result.id)
        if (sent == null) {
            output.line("The bot did not report a message for result $sendIndex.")
            return
        }
        remember(listOf(sent))
        output.renderMessages(listOf(sent), messageStyler)
    }

    private fun listMembers(arguments: List<String>) {
        val peer = arguments.firstOrNull()
        val reference = peer ?: requireChat()?.reference ?: return
        output.renderParticipants(useCases.chatMembers().list(reference, MEMBER_LIMIT))
    }

    private fun listFolders() {
        output.renderFolders(useCases.listFolders().list())
    }

    /**
     * Lists the files of the chat the line names, or of the one the prompt shows.
     *
     * `--kind` names the media filter and `--limit` the page size, both as the one-shot command takes
     * them; an unknown kind is reported by the parser the command shares.
     */
    private fun listFiles(arguments: List<String>) {
        val peer = wordsOf(arguments).firstOrNull() ?: requireChat()?.reference ?: return
        val kind = mediaFileKindOf(valueAfter(arguments, KIND_OPTION) ?: DEFAULT_FILE_KIND)
        // `--kind` is parsed above, so the limit check only sees the `--limit` a line carries.
        val limit = pageLimit(without(arguments, KIND_OPTION), FILES_USAGE) ?: return
        output.renderFiles(useCases.searchMessages().files(peer, kind, limit))
    }

    /**
     * Writes a message's media to a local file, the one-shot command's behaviour in one line.
     *
     * Without a target the media's own name is used in the working directory, as the command does,
     * and the line reports the path and the byte count it holds.
     */
    private fun downloadMedia(arguments: List<String>) {
        val peer = arguments.getOrNull(0)
        val messageId = arguments.getOrNull(1)?.toIntOrNull()
        if (peer == null || messageId == null) {
            output.line(DOWNLOAD_USAGE)
            return
        }
        val useCase = useCases.downloadMedia()
        val target = arguments.getOrNull(2)?.let(Path::of)
            ?: Path.of(downloadFileName(useCase.fileName(peer, messageId), messageId))
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        val path = useCase.download(peer, messageId, target)
        output.line("$path\t${Files.size(path)}")
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

    /**
     * The `--limit` a verb carries, or [TRANSCRIPT_LIMIT]; a value that is not a positive number
     * reports [usage] and gives nothing, the way the shell reports every other malformed line.
     */
    private fun pageLimit(arguments: List<String>, usage: String): Int? {
        if (arguments.isEmpty()) return TRANSCRIPT_LIMIT
        val index = arguments.indexOf(LIMIT_OPTION)
        val limit = arguments.getOrNull(index + 1)?.toIntOrNull()
        if (index < 0 || limit == null || limit <= 0) {
            output.line(usage)
            return null
        }
        return limit
    }

    /** The words of [arguments] that are neither an option nor the value it was given. */
    private fun wordsOf(arguments: List<String>): List<String> {
        val words = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            val word = arguments[index]
            when {
                valueAfter(arguments, word) != null -> index++
                word.startsWith(OPTION_PREFIX) -> Unit
                else -> words += word
            }
            index++
        }
        return words
    }

    /** The word that follows [option], or null when [option] is absent or has no value after it. */
    private fun valueAfter(arguments: List<String>, option: String): String? {
        if (!option.startsWith(OPTION_PREFIX)) return null
        val index = arguments.indexOf(option)
        val value = arguments.getOrNull(index + 1) ?: return null
        return value.takeUnless { it.startsWith(OPTION_PREFIX) }
    }

    /** The arguments without [option] and the value it was given. */
    private fun without(arguments: List<String>, option: String): List<String> {
        val index = arguments.indexOf(option)
        return if (index < 0) arguments else arguments.filterIndexed { i, _ -> i != index && i != index + 1 }
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
        const val OPTION_PREFIX = "--"
        const val LIMIT_OPTION = "--limit"
        const val KIND_OPTION = "--kind"
        const val SEND_OPTION = "--send"
        const val DIALOG_LIMIT = 100
        const val CONTACT_LIMIT = 50

        /** How many members `members` asks for, matching the one-shot command's default. */
        const val MEMBER_LIMIT = 50

        /** How many messages of the current chat the shell keeps in view. */
        const val TRANSCRIPT_LIMIT = 20

        const val NO_CHAT_PROMPT = "> "

        const val READ_USAGE = "Usage: read [--limit N]"

        const val FILES_USAGE = "Usage: files [<peer>] [--kind <kind>] [--limit N]"

        const val DOWNLOAD_USAGE = "Usage: download-media <peer> <message-id> [target]"

        val HELP = listOf(
            "help                 show this help",
            "dialogs | list       list conversations",
            "open <peer>          open a chat",
            "read [--limit N]     read the current chat",
            "send <text...>       send a message",
            "reply <id> <text...> reply to a message",
            "contacts             list contacts",
            "search <query>       search the current chat, or everywhere",
            "files [<peer>] [--kind <kind>] [--limit N]  list the files of a chat",
            "download-media <peer> <message-id> [target]  save a message's media",
            "stickers             list the installed sticker sets",
            "sticker-set <set>    show a set with its stickers numbered",
            "send-sticker <set> <index>  send a sticker in the current chat",
            "inline <bot> <query> [--send <index>]  query a bot, send a result here",
            "members [<peer>]     list the current chat's members",
            "folders              list the dialog folders",
            "quit | exit          leave the shell",
        )
    }
}
