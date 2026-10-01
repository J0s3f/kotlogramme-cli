package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.TelegramException
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.domain.MediaFileKind
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads the media of a message to a local file.
 *
 * Without a target the file is named after the media's own name, falling back to the message id
 * when the media carries none, and lands in the working directory. Missing parent directories are
 * created and an existing file is overwritten, so a target that is a fresh path and one that is
 * already there both work. A message that has no media, a peer that does not resolve and a message
 * id Telegram cannot find are all reported as one-line errors, never as a stack trace.
 *
 * The result is one line: the path, a tab and the byte count the file holds on disk, which is what a
 * script reads and what a person sees.
 */
class DownloadMediaCommand : CliktCommand(name = "download-media") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val messageId by argument("messageId", help = "The message whose media to download").int()
    private val requested by argument(
        "target",
        help = "Where to write it; defaults to the media's own name in the working directory",
    ).optional()

    override fun run() {
        val (path, size) = try {
            rejectInvalidInput { download() }
        } catch (error: TelegramException) {
            // TDLib rejects the download itself - a message that has no media or does not resolve -
            // and the facade reports that as this; the command names what it was trying to do.
            throw UsageError("Could not download the media of message $messageId in $peer: ${error.message}")
        }
        appContext.output.line("$path\t$size")
    }

    private fun download(): Pair<Path, Long> {
        val downloadMedia = appContext.downloadMedia()
        val target = requested?.let(Path::of) ?: defaultTarget(downloadMedia.fileName(peer, messageId))
        createParentDirectory(target)
        val path = downloadMedia.download(peer, messageId, target)
        return path to Files.size(path)
    }

    /** The target the media's own name names, which the working directory is the parent of. */
    private fun defaultTarget(mediaName: String?): Path =
        Path.of(downloadFileName(mediaName, messageId))

    private fun createParentDirectory(target: Path) {
        val parent = target.toAbsolutePath().parent ?: return
        Files.createDirectories(parent)
    }
}

/**
 * Lists the files a chat holds, filtered by media kind on Telegram's side.
 *
 * The filter is applied by the server, not here: a history scan only reaches as far as it read, so a
 * listing asks Telegram for the kind and gets every message of it in the chat, however far back it
 * is. The listing therefore shows whatever the account is allowed to see, which for a chat the
 * account has left is as much as Telegram still serves it.
 *
 * `--kind` names the filter; an unknown name is a usage error listing the valid ones. `--total`
 * prints just how many files the chat holds, which may exceed the page `--limit` returns.
 */
class ListFilesCommand : CliktCommand(name = "list-files") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val kind by option("--kind", help = "Which files to list: ${fileKindNames()}").default(DEFAULT_KIND)
    private val limit by option("--limit", help = "How many files to list").int().default(DEFAULT_LIMIT)
    private val total by option("--total", help = "Print only how many files the chat holds").flag()

    override fun run() {
        val search = appContext.searchMessages()
        val files = rejectInvalidInput { mediaFileKindOf(kind) }
        if (total) {
            appContext.output.line(search.fileTotal(peer, files).toString())
        } else {
            appContext.output.renderFiles(rejectInvalidInput { search.files(peer, files, limit) })
        }
    }

    private companion object {
        const val DEFAULT_KIND = DEFAULT_FILE_KIND

        const val DEFAULT_LIMIT = 20
    }
}

/** The files a listing shows when no kind is given: photos and videos, as the clients list them. */
internal const val DEFAULT_FILE_KIND = "photo-video"

/**
 * The kind a `--kind` name asks for, or a rejection naming the valid ones.
 *
 * A [MediaFileKind.cliName] is the canonical spelling; the aliases are the plurals and the words
 * people reach for. The message is what a user sees, so it lists the names that work.
 */
internal fun mediaFileKindOf(name: String): MediaFileKind =
    FILE_KIND_ALIASES[name]
        ?: MediaFileKind.entries.firstOrNull { it.cliName == name }
        ?: throw IllegalArgumentException("unknown file kind '$name'; choose ${fileKindNames()}")

/** The kinds a listing can ask for, as the one line the option's help shows. */
internal fun fileKindNames(): String = MediaFileKind.entries.joinToString(", ") { it.cliName }

/**
 * The file name to write a download to when no target was given: the media's own name reduced to a
 * bare file name, or the message id when it carries no usable name.
 */
internal fun downloadFileName(mediaName: String?, messageId: Int): String =
    mediaName?.let(::bareFileName) ?: messageId.toString()

/**
 * [name] as a single path segment, or `null` when it cannot be one.
 *
 * A media name arrives from a remote sender, so it may carry directories (`../../etc/passwd`) or
 * characters no local file may hold (`a:b.txt` on Windows). Only the last segment is kept and the
 * characters a file cannot hold become `_`, so the name is written where it was asked for.
 */
private fun bareFileName(name: String): String? {
    val segment = name.trim().substringAfterLast('/').substringAfterLast('\\')
    if (segment.isEmpty() || segment == CURRENT_DIRECTORY || segment == PARENT_DIRECTORY) return null
    return segment.map { if (it.isLetterOrDigit() || it in NAME_CHARACTERS) it else '_' }.joinToString("")
}

private val FILE_KIND_ALIASES = mapOf(
    "photos" to MediaFileKind.PHOTOS,
    "file" to MediaFileKind.DOCUMENT,
    "music" to MediaFileKind.MUSIC,
)

/** The punctuation a file name may hold, beyond letters and digits. */
private const val NAME_CHARACTERS = "-_. ()[]&'+,;=@~"

private const val CURRENT_DIRECTORY = "."

private const val PARENT_DIRECTORY = ".."
