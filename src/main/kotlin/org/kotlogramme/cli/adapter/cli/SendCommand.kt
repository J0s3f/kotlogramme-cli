package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.InputStream
import org.kotlogramme.cli.adapter.format.renderMessages

/**
 * Sends a text message.
 *
 * The words after the peer are joined with spaces, so `send @ada hello there` sends "hello there".
 * A single `-` reads the whole message from standard input instead, which is what makes piping into
 * the client possible.
 */
class SendCommand(
    private val stdin: InputStream = System.`in`,
) : CliktCommand(name = "send") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val text by argument("text", help = "The message, or - to read it from stdin").multiple(
        required = true,
    )
    private val replyTo by option("--reply-to", help = "Reply to this message id").int()
    private val silent by option("--silent", help = "Send without a notification").flag()

    override fun run() {
        val body = messageText()
        val sent = rejectInvalidInput { appContext.messageWriter().sendText(peer, body, replyTo, silent) }
        appContext.output.renderMessages(listOf(sent), appContext.messageStyler)
    }

    private fun messageText(): String =
        if (text.size == 1 && text.single() == STDIN) readStdin() else text.joinToString(" ")

    /**
     * Reads the message from standard input as UTF-8.
     *
     * The bytes are read directly rather than through [com.github.ajalt.clikt.core.CliktCommand.terminal],
     * whose reader decodes with the JVM's default charset. That default follows the platform's ANSI code
     * page (Cp1252 on a German Windows, for example), so a pipe or a redirect carrying UTF-8 - which is
     * what `cat msg.txt |`, `curl |` and every editor's "pipe selection" produce - would be read as
     * mojibake. Telegram text is overwhelmingly non-ASCII, so decoding as UTF-8 is the only sane default.
     */
    private fun readStdin(): String = String(stdin.readAllBytes(), Charsets.UTF_8)

    private companion object {
        const val STDIN = "-"
    }
}
