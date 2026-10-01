package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.progressEnabled
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.media.SpoolFile
import org.kotlogramme.cli.adapter.media.mediaKindOf
import org.kotlogramme.cli.application.port.spi.MediaKindHint
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.domain.AlbumItem
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
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
 * A `-` path reads standard input and uploads it under the name `--name` gives, defaulting to
 * `stdin`, because Telegram needs a file name for an upload that has no path. Piped input is read as
 * raw bytes rather than as text, and spooled to a temporary file that is deleted once the upload
 * ends: the bytes Telegram sees are the bytes that were piped, and a file larger than the heap still
 * uploads. A piped file is never streamable, so it is sent as a document, or as a photo with
 * `--detect` when `--name` names one.
 *
 * `--reply-to` quotes an existing message and `--silent` suppresses the notification; both reach the
 * service for every form of the send, including a stdin stream.
 *
 * `--progress` and `--no-progress` choose whether the upload shows a bar as it goes. The bar is on by
 * default where the output is a terminal and off in a pipeline, so a script gets no carriage returns
 * in its output; either flag overrides that, and `--no-progress` wins if both are given.
 */
class SendFileCommand(
    /** The pipe a `-` path reads: the process's own, or whatever a caller hands in. */
    private val stdin: InputStream = System.`in`,
) : CliktCommand(name = "send-file") {
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
    private val progress by option("--progress", help = "Show an upload progress bar even where the output is not a terminal").flag()
    private val noProgress by option("--no-progress", help = "Never show an upload progress bar, even on a terminal").flag()

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

    private fun send(): Message {
        val reporter = uploadProgress()
        return when {
            path == STDIN -> sendStdin(reporter)
            photo -> appContext.sendMedia().sendFile(
                peer,
                Path.of(path),
                caption,
                asPhoto = true,
                replyToMessageId = replyTo,
                silent = silent,
                progress = reporter,
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
                reporter,
            )
            noDetect -> appContext.sendMedia().sendFile(
                peer,
                Path.of(path),
                caption,
                asPhoto = false,
                replyToMessageId = replyTo,
                silent = silent,
                progress = reporter,
            )
            else -> sendDetected(Path.of(path), reporter)
        }
    }

    private fun sendStdin(reporter: UploadProgressReporter): Message {
        val uploadName = name ?: STDIN_NAME
        val asPhoto = detect && mediaKindOf(uploadName) == MediaKindHint.PHOTO
        // The spool file is what makes a pipe uploadable: it carries the bytes Telegram has to be
        // told about, and it goes as soon as the send ends, whichever way the send ended.
        return SpoolFile.of(stdin).use { spool ->
            spool.open().use { payload ->
                appContext.sendMedia()
                    .sendStream(peer, uploadName, payload, caption, asPhoto, replyTo, silent, spool.size, reporter)
            }
        }
    }

    private fun sendDetected(file: Path, reporter: UploadProgressReporter): Message {
        val probe = appContext.mediaProbe().probe(file)
        return when (probe.kind) {
            MediaKindHint.PHOTO -> appContext.sendMedia().sendFile(
                peer,
                file,
                caption,
                asPhoto = true,
                replyToMessageId = replyTo,
                silent = silent,
                progress = reporter,
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
                reporter,
            )
            MediaKindHint.DOCUMENT -> appContext.sendMedia().sendFile(
                peer,
                file,
                caption,
                asPhoto = false,
                replyToMessageId = replyTo,
                silent = silent,
                progress = reporter,
            )
        }
    }

    private fun uploadProgress(): UploadProgressReporter =
        appContext.uploadProgress(progressEnabled(progress, noProgress, appContext.isInteractiveTerminal))

    private fun isMetadataAbsent() = duration == null && width == null && height == null

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

/**
 * Sends several files as one grouped message.
 *
 * Each path is probed by default so a photo goes out as a photo and anything else as a document;
 * `--photo` forces every item as a photo. `--caption` captions the first item, which is where
 * Telegram shows a group's caption. An album holds one to ten items, and the facade's album carries
 * no reply-to or silent flag, so neither is offered.
 */
class SendAlbumCommand : CliktCommand(name = "send-album") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val paths by argument("path", help = "One or more files to send together").multiple(required = true)
    private val caption by option("--caption", help = "The caption, shown on the first item").default("")
    private val photo by option("--photo", help = "Send every item as a photo").flag()

    override fun run() {
        val items = rejectInvalidInput { albumItems() }
        val sent = rejectInvalidInput { appContext.sendMedia().sendAlbum(peer, items) }
        appContext.output.renderMessages(sent, appContext.messageStyler)
    }

    private fun albumItems(): List<AlbumItem> = paths.mapIndexed { index, path ->
        val file = Path.of(path)
        AlbumItem(
            path = file,
            caption = if (index == 0) caption else "",
            asPhoto = photo || appContext.mediaProbe().probe(file).kind == MediaKindHint.PHOTO,
        )
    }
}
