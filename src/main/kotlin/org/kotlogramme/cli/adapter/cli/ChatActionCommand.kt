package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import org.kotlogramme.cli.domain.ChatActivity

/**
 * Reports a chat action — a typing indicator or one of the upload/recording statuses — to a chat.
 *
 * The status is one-shot, as Telegram's own is: it clears after a few seconds, so a script that
 * wants it held re-sends it.
 */
class ChatActionCommand : CliktCommand(name = "chat-action") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val action by option("--action", help = "The status: ${chatActivityNames()}")
        .default(ChatActivity.TYPING.cliName)

    override fun run() {
        val activity = rejectInvalidInput { chatActivityOf(action) }
        rejectInvalidInput { appContext.messageWriter().sendChatAction(peer, activity) }
        appContext.output.line("Sent the '$action' action to $peer.")
    }
}

/**
 * The activity a `--action` name asks for, or a rejection naming the valid ones.
 *
 * The name is what a user types, so the message lists the names that work rather than the enum
 * constants.
 */
internal fun chatActivityOf(name: String): ChatActivity =
    ChatActivity.entries.firstOrNull { it.cliName == name }
        ?: throw IllegalArgumentException("unknown chat action '$name'; choose ${chatActivityNames()}")

/** The activities a `--action` may name, as the one line the option's help shows. */
internal fun chatActivityNames(): String = ChatActivity.entries.joinToString(", ") { it.cliName }
