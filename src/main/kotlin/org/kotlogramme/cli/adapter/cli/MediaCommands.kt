package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages
import java.io.ByteArrayInputStream
import java.nio.file.Path

/**
 * Sends a local file, or the bytes piped on standard input, as a document or a photo.
 *
 * A `-` path reads standard input and uploads it as a stream named by `--name`, defaulting to
 * `stdin`, because Telegram needs a file name for an upload that has no path. Any other path is
 * handed to the gateway as-is, so its file name names the upload.
 */
class SendFileCommand : CliktCommand(name = "send-file") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val path by argument("path", help = "The file to send, or - to read the bytes from stdin")
    private val caption by option("--caption", help = "The caption").default("")
    private val photo by option("--photo", help = "Send as a photo instead of a document").flag()
    private val name by option("--name", help = "The upload file name for a stdin stream; defaults to stdin")

    override fun run() {
        val message = rejectInvalidInput { send() }
        appContext.output.renderMessages(listOf(message))
    }

    private fun send() =
        if (path == STDIN) {
            val data = ByteArrayInputStream(readStdin().toByteArray())
            appContext.sendMedia().sendStream(peer, name ?: STDIN_NAME, data, caption, photo)
        } else {
            appContext.sendMedia().sendFile(peer, Path.of(path), caption, photo)
        }

    private fun readStdin(): String = generateSequence { terminal.readLineOrNull(false) }.joinToString("\n")

    private companion object {
        const val STDIN = "-"
        const val STDIN_NAME = "stdin"
    }
}

/** Sends media that Telegram fetches from a URL. */
class SendMediaUrlCommand : CliktCommand(name = "send-media-url") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val url by argument("url", help = "The URL Telegram fetches")
    private val caption by option("--caption", help = "The caption").default("")
    private val photo by option("--photo", help = "Send as a photo instead of a document").flag()

    override fun run() {
        val message = rejectInvalidInput {
            require(url.isNotBlank()) { "url must not be blank" }
            appContext.sendMedia().sendUrl(peer, url, caption, photo)
        }
        appContext.output.renderMessages(listOf(message))
    }
}

/** Re-sends the media of an existing message without uploading it again. */
class CopyMediaCommand : CliktCommand(name = "copy-media") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message whose media to re-send").int()
    private val caption by option("--caption", help = "A new caption").default("")

    override fun run() {
        val message = rejectInvalidInput { appContext.sendMedia().copyMedia(peer, messageId, caption) }
        appContext.output.renderMessages(listOf(message))
    }
}
