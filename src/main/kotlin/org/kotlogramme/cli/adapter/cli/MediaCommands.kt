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
 * A real file's kind is detected by default. The extension picks the kind — `jpg`, `jpeg` and `png`
 * are photos, `mp4`, `m4v`, `mov`, `mkv`, `webm` and `avi` are videos, anything else a document —
 * and a video supplies the duration, width and height read from its container, so a `.mp4` goes out
 * as a streamable video with its metadata without any flag. `--detect` spells that default out and
 * `--no-detect` turns it off, sending the bytes as a plain document with no kind inference and no
 * metadata.
 *
 * `--photo` and `--video` force the kind and win over detection. Each is mutually exclusive with the
 * other and with `--detect`/`--no-detect`. Explicit `--duration`, `--width` and `--height` describe
 * a video; they are only accepted with `--video` or `--detect`, where they override the probe.
 *
 * A `-` path reads standard input and uploads it as a stream named by `--name`, defaulting to
 * `stdin`, because Telegram needs a file name for an upload that has no path. A piped file is never
 * streamable, so it is sent as a document, or as a photo with `--detect` when `--name` names one.
 *
 * `--reply-to` quotes an existing message and `--silent` suppresses the notification; both reach the
 * service for every form of the send, including a stdin stream.
 */
class SendFileCommand : CliktCommand(name = "send-file") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val path by argument("path", help = "The file to send, or - to read the bytes from stdin")
    private val caption by option("--caption", help = "The caption").default("")
    private val photo by option("--photo", help = "Force a photo send, overriding detection").flag()
    private val video by option("--video", help = "Force a streamable video send, overriding detection").flag()
    private val detect by option("--detect", help = "Detect the kind and the video metadata (the default for a real file)").flag()
    private val noDetect by option("--no-detect", help = "Send a real file as a plain document without detecting its kind").flag()
    private val duration by option("--duration", help = "The video duration in seconds; only with --video or --detect")
        .double()
    private val width by option("--width", help = "The video width in pixels; only with --video or --detect").int()
    private val height by option("--height", help = "The video height in pixels; only with --video or --detect").int()
    private val name by option("--name", help = "The upload file name for a stdin stream; defaults to stdin")
    private val replyTo by option("--reply-to", help = "Reply to this message id").int()
    private val silent by option("--silent", help = "Send without a notification").flag()

    override fun run() {
        val message = rejectInvalidInput {
            require(!(video && photo)) { "--video and --photo are mutually exclusive" }
            require(!(detect && noDetect)) { "--detect and --no-detect are mutually exclusive" }
            require(!(detect && (video || photo))) { "--detect cannot be combined with --video or --photo" }
            require(!(noDetect && (video || photo))) { "--no-detect cannot be combined with --video or --photo" }
            require(video || detect || isMetadataAbsent()) {
                "--duration, --width and --height require --video or --detect"
            }
            send()
        }
        appContext.output.renderMessages(listOf(message), appContext.messageStyler)
    }

    private fun send(): Message =
        when {
            path == STDIN -> sendStdin()
            photo -> appContext.sendMedia().sendFile(
                peer,
                Path.of(path),
                caption,
                asPhoto = true,
                replyToMessageId = replyTo,
                silent = silent,
            )
            video -> appContext.sendMedia().sendVideo(
                peer,
                Path.of(path),
                caption,
                duration,
                width,
                height,
                replyTo,
                silent,
            )
            noDetect -> appContext.sendMedia().sendFile(
                peer,
                Path.of(path),
                caption,
                asPhoto = false,
                replyToMessageId = replyTo,
                silent = silent,
            )
            else -> sendDetected(Path.of(path))
        }

    private fun sendStdin(): Message {
        val uploadName = name ?: STDIN_NAME
        val data = ByteArrayInputStream(readStdin().toByteArray())
        val asPhoto = detect && mediaKindOf(uploadName) == MediaKindHint.PHOTO
        return appContext.sendMedia().sendStream(peer, uploadName, data, caption, asPhoto, replyTo, silent)
    }

    private fun sendDetected(file: Path): Message {
        val probe = appContext.mediaProbe().probe(file)
        return when (probe.kind) {
            MediaKindHint.PHOTO -> appContext.sendMedia().sendFile(
                peer,
                file,
                caption,
                asPhoto = true,
                replyToMessageId = replyTo,
                silent = silent,
            )
            MediaKindHint.VIDEO -> appContext.sendMedia().sendVideo(
                peer,
                file,
                caption,
                duration ?: probe.durationSeconds,
                width ?: probe.width,
                height ?: probe.height,
                replyTo,
                silent,
            )
            MediaKindHint.DOCUMENT -> appContext.sendMedia().sendFile(
                peer,
                file,
                caption,
                asPhoto = false,
                replyToMessageId = replyTo,
                silent = silent,
            )
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
    private val replyTo by option("--reply-to", help = "Reply to this message id").int()
    private val silent by option("--silent", help = "Send without a notification").flag()

    override fun run() {
        val message = rejectInvalidInput {
            require(url.isNotBlank()) { "url must not be blank" }
            appContext.sendMedia().sendUrl(peer, url, caption, photo, replyTo, silent)
        }
        appContext.output.renderMessages(listOf(message), appContext.messageStyler)
    }
}

/** Re-sends the media of an existing message without uploading it again. */
class CopyMediaCommand : CliktCommand(name = "copy-media") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message whose media to re-send").int()
    private val caption by option("--caption", help = "A new caption").default("")
    private val replyTo by option("--reply-to", help = "Reply to this message id").int()
    private val silent by option("--silent", help = "Send without a notification").flag()

    override fun run() {
        val message = rejectInvalidInput {
            appContext.sendMedia().copyMedia(peer, messageId, caption, replyTo, silent)
        }
        appContext.output.renderMessages(listOf(message), appContext.messageStyler)
    }
}
