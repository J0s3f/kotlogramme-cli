package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.format.MessageStyler
import org.kotlogramme.cli.adapter.format.renderBlockedContacts
import org.kotlogramme.cli.adapter.format.renderChats
import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.adapter.format.renderFolders
import org.kotlogramme.cli.adapter.format.renderInlineResults
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.format.renderParticipants
import org.kotlogramme.cli.adapter.format.renderSessions
import org.kotlogramme.cli.adapter.format.renderStickerSet
import org.kotlogramme.cli.adapter.format.renderStickerSets
import org.kotlogramme.cli.adapter.cli.DEFAULT_FILE_KIND
import org.kotlogramme.cli.adapter.cli.chatActivityNames
import org.kotlogramme.cli.adapter.cli.chatActivityOf
import org.kotlogramme.cli.adapter.cli.decodeEmojiArgument
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
                "help" -> printHelp(arguments)
                "dialogs", "list" -> listDialogs()
                "open" -> open(arguments)
                "read" -> read(arguments)
                "send" -> send(arguments, replyToMessageId = null)
                "reply" -> reply(arguments)
                "edit" -> edit(arguments)
                "delete" -> delete(arguments)
                "forward" -> forward(arguments)
                "pin" -> pin(arguments)
                "unpin" -> unpin(arguments)
                "pinned" -> pinned()
                "react" -> react(arguments)
                "unreact" -> unreact(arguments)
                "chat-action" -> chatAction(arguments)
                "mark-read" -> markRead()
                "invite" -> invite(arguments)
                "kick" -> kick(arguments)
                "contacts" -> listContacts()
                "blocked" -> listBlocked()
                "search" -> search(arguments)
                "files" -> listFiles(arguments)
                "download-media" -> downloadMedia(arguments)
                "send-media-url" -> sendMediaUrl(arguments)
                "copy-media" -> copyMedia(arguments)
                "stickers" -> listStickerSets()
                "sticker-set" -> showStickerSet(arguments)
                "send-sticker" -> sendSticker(arguments)
                "inline" -> inline(arguments)
                "members" -> listMembers(arguments)
                "folders" -> listFolders()
                "sessions" -> sessions(arguments)
                else -> output.line("Unknown command: $name. Type `help` for the available commands.")
            }
        } catch (error: Exception) {
            output.line(error.message ?: error.toString())
        }
    }

    /**
     * Shows the shell's own help, or with `commands` the whole CLI's command set.
     *
     * The CLI list is a hand-maintained copy: `help commands` exists because the shell is where a
     * user discovers what the tool can do, but the list is not generated from the command tree.
     */
    private fun printHelp(arguments: List<String>) {
        if (arguments.firstOrNull()?.lowercase() == COMMANDS_HELP) {
            CLI_COMMANDS.forEach(output::line)
            return
        }
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

    /**
     * Replaces a message's text in the current chat and keeps the shown transcript honest.
     *
     * The port returns the edited message, so it replaces the stale copy the transcript holds rather
     * than leaving the old text on screen; the edited message is rendered as well.
     */
    private fun edit(arguments: List<String>) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        val text = arguments.drop(1).joinToString(" ").trim()
        if (messageId == null || text.isEmpty()) {
            output.line("Usage: edit <id> <text...>")
            return
        }
        val edited = useCases.messageWriter().edit(chat.reference, messageId, text)
        remember(listOf(edited))
        output.renderMessages(listOf(edited), messageStyler)
    }

    private fun delete(arguments: List<String>) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        if (messageId == null) {
            output.line("Usage: delete <id>")
            return
        }
        val deleted = useCases.messageWriter().delete(chat.reference, listOf(messageId))
        transcript.removeAll { it.id == messageId }
        output.line("Deleted $deleted message(s).")
    }

    /** Forwards a message of the current chat to another peer, the `--to` of the one-shot command. */
    private fun forward(arguments: List<String>) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        val to = arguments.getOrNull(1)
        if (messageId == null || to == null) {
            output.line("Usage: forward <id> <to>")
            return
        }
        val forwarded = useCases.messageWriter().forward(chat.reference, listOf(messageId), to)
        output.renderMessages(forwarded, messageStyler)
    }

    private fun pin(arguments: List<String>) = messageIdVerb(arguments, "pin") { chat, id ->
        useCases.messageWriter().pin(chat, id)
        output.line("Pinned message $id.")
    }

    /**
     * Unpins one message, or with `all` every pinned message in the current chat.
     *
     * `all` is a word rather than an id: a chat's ids are numbers, so the two never collide, and the
     * one-shot command's `--all` reads oddly in a REPL line.
     */
    private fun unpin(arguments: List<String>) {
        val chat = requireChat() ?: return
        if (arguments.singleOrNull()?.equals(ALL, ignoreCase = true) == true) {
            useCases.messageWriter().unpinAll(chat.reference)
            output.line("Unpinned every message in ${chat.title}.")
            return
        }
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        if (messageId == null) {
            output.line("Usage: unpin <id> | unpin all")
            return
        }
        useCases.messageWriter().unpin(chat.reference, messageId)
        output.line("Unpinned message $messageId.")
    }

    /** Shows the current chat's pinned message, or says that it has none. */
    private fun pinned() {
        val chat = requireChat() ?: return
        val message = useCases.messageWriter().pinnedMessage(chat.reference)
        if (message == null) {
            output.line("No pinned message in ${chat.title}.")
        } else {
            output.renderMessages(listOf(message), messageStyler)
        }
    }

    /**
     * Reports a chat action to the current chat; the action defaults to the typing indicator.
     *
     * The one-shot command takes a peer and an `--action`; in the REPL the open chat is the target,
     * so the line is just the action and the shared parser rejects a name that is not a status.
     */
    private fun chatAction(arguments: List<String>) {
        val chat = requireChat() ?: return
        val name = arguments.firstOrNull() ?: DEFAULT_CHAT_ACTION
        val activity = chatActivityOf(name)
        useCases.messageWriter().sendChatAction(chat.reference, activity)
        output.line("Sent the '$name' action to ${chat.title}.")
    }

    /** Reacts to a message; the emoji is decoded the way the one-shot command decodes it. */
    private fun react(arguments: List<String>) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        val raw = arguments.getOrNull(1)
        if (messageId == null || raw == null) {
            output.line("Usage: react <id> <emoji>")
            return
        }
        val emoji = decodeEmojiArgument(raw)
        useCases.messageWriter().react(chat.reference, messageId, emoji)
        output.line("Reacted to message $messageId.")
    }

    private fun unreact(arguments: List<String>) = messageIdVerb(arguments, "unreact") { chat, id ->
        useCases.messageWriter().removeReaction(chat, id)
        output.line("Removed the reaction from message $id.")
    }

    private fun markRead() {
        val chat = requireChat() ?: return
        useCases.messageWriter().markRead(chat.reference)
        output.line("Marked ${chat.title} as read.")
    }

    private fun invite(arguments: List<String>) {
        val chat = requireChat() ?: return
        val user = arguments.firstOrNull()
        if (user == null) {
            output.line("Usage: invite <user>")
            return
        }
        useCases.chatMembers().invite(chat.reference, user)
        output.line("Invited $user to ${chat.title}.")
    }

    private fun kick(arguments: List<String>) {
        val chat = requireChat() ?: return
        val user = arguments.firstOrNull()
        if (user == null) {
            output.line("Usage: kick <user>")
            return
        }
        useCases.chatMembers().kick(chat.reference, user)
        output.line("Kicked $user from ${chat.title}.")
    }

    /** Runs a verb that takes one message id in the current chat, reporting its usage when absent. */
    private fun messageIdVerb(arguments: List<String>, name: String, action: (String, Int) -> Unit) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        if (messageId == null) {
            output.line("Usage: $name <id>")
            return
        }
        action(chat.reference, messageId)
    }

    private fun listContacts() {
        output.renderContacts(useCases.contacts().list(CONTACT_LIMIT))
    }

    /** Lists the blocked accounts, the read-only half of the contact surface. */
    private fun listBlocked() {
        output.renderBlockedContacts(useCases.contacts().blocked(BLOCKED_LIMIT))
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
     * Lists the account's active sessions, or drops one with `terminate <hash>`.
     *
     * The hash is the exact one the listing reported, matching the one-shot command; `terminate-all`
     * is deliberately absent because a REPL line cannot carry the `--yes` confirmation that guards it.
     */
    private fun sessions(arguments: List<String>) {
        val sessions = useCases.sessions()
        val verb = arguments.firstOrNull()
        if (verb == null) {
            output.renderSessions(sessions.list())
            return
        }
        if (!verb.equals(TERMINATE, ignoreCase = true)) {
            output.line("Usage: sessions [terminate <hash>]")
            return
        }
        val hash = arguments.getOrNull(1)?.toLongOrNull()
        if (hash == null) {
            output.line("Usage: sessions terminate <hash>")
            return
        }
        sessions.terminate(hash)
        output.line("Terminated session $hash.")
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

    /** Sends media Telegram fetches from a URL into the current chat. */
    private fun sendMediaUrl(arguments: List<String>) {
        val chat = requireChat() ?: return
        val url = arguments.firstOrNull()
        if (url == null) {
            output.line("Usage: send-media-url <url>")
            return
        }
        val sent = useCases.sendMedia().sendUrl(chat.reference, url, "", asPhoto = false, replyToMessageId = null, silent = false)
        remember(listOf(sent))
        output.renderMessages(listOf(sent), messageStyler)
    }

    /** Re-sends a message's media in the current chat without uploading it again. */
    private fun copyMedia(arguments: List<String>) {
        val chat = requireChat() ?: return
        val messageId = arguments.firstOrNull()?.toIntOrNull()
        if (messageId == null) {
            output.line("Usage: copy-media <id>")
            return
        }
        val sent = useCases.sendMedia().copyMedia(chat.reference, messageId, "", replyToMessageId = null, silent = false)
        remember(listOf(sent))
        output.renderMessages(listOf(sent), messageStyler)
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
        const val COMMANDS_HELP = "commands"
        const val ALL = "all"
        const val TERMINATE = "terminate"
        const val DEFAULT_CHAT_ACTION = "typing"
        const val DIALOG_LIMIT = 100
        const val CONTACT_LIMIT = 50

        /** How many blocked accounts `blocked` asks for, matching the one-shot command's default. */
        const val BLOCKED_LIMIT = 50

        /** How many members `members` asks for, matching the one-shot command's default. */
        const val MEMBER_LIMIT = 50

        /** How many messages of the current chat the shell keeps in view. */
        const val TRANSCRIPT_LIMIT = 20

        const val NO_CHAT_PROMPT = "> "

        const val READ_USAGE = "Usage: read [--limit N]"

        const val FILES_USAGE = "Usage: files [<peer>] [--kind <kind>] [--limit N]"

        const val DOWNLOAD_USAGE = "Usage: download-media <peer> <message-id> [target]"

        val HELP = listOf(
            "help [commands]       show this help, or the whole CLI's commands",
            "dialogs | list       list conversations",
            "open <peer>          open a chat",
            "read [--limit N]     read the current chat",
            "send <text...>       send a message",
            "reply <id> <text...> reply to a message",
            "edit <id> <text...>  edit a message in the current chat",
            "delete <id>          delete a message in the current chat",
            "forward <id> <to>    forward a message to another peer",
            "pin <id> | unpin <id>  pin or unpin a message",
            "unpin all            unpin every message in the current chat",
            "pinned               show the current chat's pinned message",
            "react <id> <emoji> | unreact <id>  react to a message",
            "chat-action [<action>]  report a chat status (default typing)",
            "mark-read            mark the current chat read",
            "invite <user>        add a user to the current chat",
            "kick <user>          remove a user from the current chat",
            "contacts             list contacts",
            "blocked              list the blocked accounts",
            "search <query>       search the current chat, or everywhere",
            "files [<peer>] [--kind <kind>] [--limit N]  list the files of a chat",
            "download-media <peer> <message-id> [target]  save a message's media",
            "send-media-url <url>  send media Telegram fetches from a URL",
            "copy-media <id>      re-send a message's media here",
            "stickers             list the installed sticker sets",
            "sticker-set <set>    show a set with its stickers numbered",
            "send-sticker <set> <index>  send a sticker in the current chat",
            "inline <bot> <query> [--send <index>]  query a bot, send a result here",
            "members [<peer>]     list the current chat's members",
            "folders              list the dialog folders",
            "sessions [terminate <hash>]  list active sessions, or drop one",
            "quit | exit          leave the shell",
        )

        /**
         * The whole CLI's command set, for `help commands`.
         *
         * This is the CLI's command tree, not the shell's verb set: every entry runs as
         * `kotlogramme <command>`, and the shell column says whether the REPL also takes it. A
         * verb that only exists in the shell (such as `open` or `reply`) is not listed here, because
         * this list answers "what can the tool do", and `help` answers "what can this prompt do".
         *
         * Hand-maintained: the shell reaches a dozen ports, the CLI has commands beyond them, and a
         * command added to [org.kotlogramme.cli.main] does not appear here on its own.
         */
        val CLI_COMMANDS = listOf(
            "CLI commands (run as `kotlogramme <command>`; the shell column names the shell verbs, if any):",
            "",
            "command                 where       what it does",
            "help                    shell       show the shell's help, or this list",
            "dialogs | list          both        list conversations",
            "history                 cli         read a chat's history",
            "open                    shell       open a chat",
            "read                    shell       read the current chat",
            "send                    both        send a text message",
            "send-file               cli         send a local file",
            "send-album              cli         send several files as one album",
            "send-media-url          both        send media Telegram fetches from a URL",
            "copy-media              both        re-send an existing message's media",
            "download-media          both        save a message's media to a file",
            "files                   shell       list the files of a chat",
            "list-files              cli         list a chat's files by kind",
            "edit                    both        edit a message",
            "delete                  both        delete messages",
            "reply                   shell       reply to a message",
            "forward                 both        forward messages to another chat",
            "pin | unpin             both        pin or unpin a message (`unpin all` in the shell)",
            "pinned                  both        show a chat's pinned message",
            "react | unreact         both        react to a message",
            "chat-action             both        report a chat status such as typing",
            "mark-read               both        mark a chat read",
            "contacts                both        list contacts",
            "search-contacts         cli         search contacts and the public directory",
            "block                   cli         block a peer",
            "unblock                 cli         unblock a peer",
            "blocked                 both        list the blocked accounts",
            "import-contacts         cli         import a phone number as a contact",
            "delete-contact          cli         remove a peer from contacts",
            "search                  both        search messages",
            "members                 both        list a chat's members",
            "invite                  cli         add a member to a chat",
            "kick                    cli         remove a member from a chat",
            "permissions             cli         show a member's rights",
            "promote                 cli         grant admin rights",
            "restrict                cli         apply restrictions",
            "listen                  cli         follow incoming updates",
            "folders                 both        list dialog folders",
            "sessions                both        list active sessions, or drop one",
            "chat-photos             cli         list a chat's photo messages",
            "profile-photos          cli         list a user's profile photos",
            "stickers                both        list the installed sticker sets",
            "sticker-set             both        show a set with its stickers numbered",
            "send-sticker            both        send a sticker into a chat",
            "inline                  both        query an inline bot",
            "login | logout          cli         account lifecycle",
            "whoami | config | doctor  cli       account and configuration",
            "shell                   cli         start the interactive shell",
            "quit | exit             shell       leave the shell",
        )
    }
}
