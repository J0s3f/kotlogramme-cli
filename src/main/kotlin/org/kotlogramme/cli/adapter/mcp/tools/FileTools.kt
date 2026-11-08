package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.MessageStyler
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.mcp.files.FilesSandbox
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.adapter.mcp.tool.BooleanParameter
import org.kotlogramme.cli.adapter.mcp.tool.CapturingTool
import org.kotlogramme.cli.adapter.mcp.tool.ChatParameter
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.StringListParameter
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolAnnotations
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.adapter.mcp.tool.WritingTool
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.domain.AlbumItem
import java.nio.file.Files

private const val SANDBOX_NOTE =
    "File names are relative to the files directory the user chose when starting the server, written as " +
        "'folder/file.ext'; nothing outside it can be read or written."

private const val FILE_PARAMETER_NOTE = "A file in the files directory, as 'folder/file.ext'."

class SendFileTool(
    private val sendMedia: () -> SendMedia,
    private val sandbox: FilesSandbox,
    private val styler: MessageStyler = MessageStyler.PLAIN,
) : WritingTool(
    name = "telegram_send_file",
    title = "Send a file",
    description = "Sends a file from the files directory to a chat as the user, as a document or as a photo. " +
        "Files over ${FilesSandbox.MAX_UPLOAD_BYTES / MEBIBYTE} MiB are refused. $SANDBOX_NOTE",
    parameters = ToolParameters(
        ChatParameter("chat"),
        StringParameter("file", FILE_PARAMETER_NOTE, required = true),
        StringParameter("caption", "Text shown with the file."),
        BooleanParameter("as_photo", "Send an image as a photo rather than as a document.", default = false),
        IntParameter("reply_to", "The id of a message in the same chat to reply to.", min = 1, max = Int.MAX_VALUE),
        BooleanParameter("silent", "Send without a notification sound.", default = false),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val path = sandbox.existingFile(arguments.string("file"))
        val sent = sendMedia().sendFile(
            arguments.string("chat"),
            path,
            arguments.stringOrNull("caption").orEmpty(),
            arguments.boolean("as_photo"),
            arguments.intOrNull("reply_to"),
            arguments.boolean("silent"),
            UploadProgressReporter.SILENT,
        )
        output.renderMessages(listOf(sent), styler)
    }
}

class SendAlbumTool(
    private val sendMedia: () -> SendMedia,
    private val sandbox: FilesSandbox,
    private val styler: MessageStyler = MessageStyler.PLAIN,
) : WritingTool(
    name = "telegram_send_album",
    title = "Send an album",
    description = "Sends up to $MAX_ALBUM_ITEMS files from the files directory as one grouped message. $SANDBOX_NOTE",
    parameters = ToolParameters(
        ChatParameter("chat"),
        StringListParameter(
            "files",
            "The files, in order. $FILE_PARAMETER_NOTE",
            maxItems = MAX_ALBUM_ITEMS,
        ),
        StringParameter("caption", "Text shown with the first file."),
        BooleanParameter("as_photo", "Send the images as photos rather than as documents.", default = false),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val asPhoto = arguments.boolean("as_photo")
        val caption = arguments.stringOrNull("caption").orEmpty()
        val items = arguments.strings("files").mapIndexed { index, file ->
            AlbumItem(sandbox.existingFile(file), if (index == 0) caption else "", asPhoto)
        }
        output.renderMessages(sendMedia().sendAlbum(arguments.string("chat"), items), styler)
    }

    private companion object {
        const val MAX_ALBUM_ITEMS = 10
    }
}

/**
 * Saves the media of a message into the files directory.
 *
 * It is offered at the read-only level because it changes nothing on the account; what it writes is a new
 * file inside the directory the user named for exactly this, and it never replaces a file. Its annotation
 * still says it is not read only, because it does write to the disk.
 */
class DownloadMediaTool(
    private val downloadMedia: () -> DownloadMedia,
    private val sandbox: FilesSandbox,
) : CapturingTool(
    name = "telegram_download_media",
    title = "Download a file",
    description = "Saves the file, photo or other media of a message into the files directory and says where. " +
        "Find message ids with telegram_list_files. It never replaces an existing file. $SANDBOX_NOTE",
    parameters = PARAMETERS,
    access = AccessLevel.READ_ONLY,
    annotations = ToolAnnotations(readOnly = false, destructive = false, idempotent = false),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val messageId = arguments.int("message_id")
        val useCase = downloadMedia()
        val name = arguments.stringOrNull("file_name") ?: suggestedName(useCase.fileName(chat, messageId), messageId)
        val target = sandbox.newFile(name)
        val written = useCase.download(chat, messageId, target)
        output.record(listOf("file" to sandbox.relativeName(written), "size" to Files.size(written).toString()))
    }

    private companion object {
        const val REPLACEMENT = '_'
        const val KEPT_PUNCTUATION = "-_. ()[]&'+,;=@~"

        val PARAMETERS = ToolParameters(
            ChatParameter("chat"),
            IntParameter("message_id", "The id of the message whose media to save.", min = 1, max = Int.MAX_VALUE, required = true),
            StringParameter(
                "file_name",
                "A plain file name to save it as. Without it the media's own name is used.",
            ),
        )

        /** A media name comes from the sender, so only its last part is kept and odd characters are replaced. */
        fun suggestedName(mediaName: String?, messageId: Int): String {
            val last = mediaName.orEmpty().trim().substringAfterLast('/').substringAfterLast('\\')
            val cleaned = last.map { if (it.isLetterOrDigit() || it in KEPT_PUNCTUATION) it else REPLACEMENT }
                .joinToString("")
                .trimEnd('.', ' ')
                .trimStart(' ')
            return cleaned.takeIf { FilesSandbox.isPlainFileName(it) } ?: "media-$messageId"
        }
    }
}

private const val MEBIBYTE = 1024 * 1024
