package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.media.mediaKindOf
import org.kotlogramme.cli.application.port.spi.MediaKindHint
import org.kotlogramme.cli.domain.Message
import java.io.ByteArrayInputStream
import java.nio.file.Path

/**
 * Sends a local file, or the bytes piped on standard input, as a document, a photo or a video.
 *
 * A `-` path reads standard input and uploads it as a stream named by `--name`, defaulting to
 * `stdin`, because Telegram needs a file name for an upload that has no path. A piped file is never
 * streamable, so `--video` and its metadata apply only to a real file. Any other path is handed to
 * the gateway as-is, so its file name names the upload.
 *
 * `--detect` asks the file itself: the extension picks the kind, and an ISO base media video
 * supplies its duration, width and height. Explicit `--duration`, `--width` and `--height` still
 * win over the probe. With `-` there is nothing to probe, so only the `--name` extension is used and
 * no metadata is sent.
 */
class SendFileCommand : CliktCommand(name = "send-file") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val path by argument("path", help = "The file to send, or - to read the bytes from stdin")
    private val caption by option("--caption", help = "The caption").default("")
    private val photo by option("--photo", help = "Send as a photo instead of a document").flag()
    private val video by option("--video", help = "Send as a streamable video instead of a document").flag()
    private val detect by option("--detect", help = "Detect the kind and the video metadata from the file").flag()
    private val duration by option("--duration", help = "The video duration in seconds; only with --video or --detect")
        .double()
    private val width by option("--width", help = "The video width in pixels; only with --video or --detect").int()
    private val height by option("--height", help = "The video height in pixels; only with --video or --detect").int()
    private val name by option("--name", help = "The upload file name for a stdin stream; defaults to stdin")

    override fun run() {
        val message = rejectInvalidInput {
            require(!(video && photo)) { "--video and --photo are mutually exclusive" }
            require(!(detect && (video || photo))) { "--detect cannot be combined with --video or --photo" }
            require(video || detect || isMetadataAbsent()) {
                "--duration, --width and --height require --video or --detect"
            }
            send()
        }
        appContext.output.renderMessages(listOf(message))
    }

    private fun send() =
        when {
            path == STDIN -> sendStdin()
            detect -> sendDetected(Path.of(path))
            video -> appContext.sendMedia().sendVideo(peer, Path.of(path), caption, duration, width, height)
            else -> appContext.sendMedia().sendFile(peer, Path.of(path), caption, photo)
        }

    private fun sendStdin(): Message {
        val uploadName = name ?: STDIN_NAME
        val data = ByteArrayInputStream(readStdin().toByteArray())
        val asPhoto = detect && mediaKindOf(uploadName) == MediaKindHint.PHOTO
        return appContext.sendMedia().sendStream(peer, uploadName, data, caption, asPhoto)
    }

    private fun sendDetected(file: Path): Message {
        val probe = appContext.mediaProbe().probe(file)
        return when (probe.kind) {
            MediaKindHint.PHOTO -> appContext.sendMedia().sendFile(peer, file, caption, asPhoto = true)
            MediaKindHint.VIDEO -> appContext.sendMedia().sendVideo(
                peer,
                file,
                caption,
                duration ?: probe.durationSeconds,
                width ?: probe.width,
                height ?: probe.height,
            )
            MediaKindHint.DOCUMENT -> appContext.sendMedia().sendFile(peer, file, caption, asPhoto = false)
        }
    }

    private fun isMetadataAbsent() = duration == null && width == null && height == null

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
