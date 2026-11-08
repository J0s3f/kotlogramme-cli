package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.mcp.files.FilesSandbox
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.spi.Clock

/**
 * Every tool the server can offer, built on the use cases the command line uses.
 *
 * Every use case is a provider, because building one opens a Telegram connection and a server that is
 * only asked to list tools should not do that. The tools carry their own access level; the server decides
 * which of them to offer, so this class lists them all. The file tools exist only when a [FilesSandbox] is
 * given, because without a directory the user chose there is nothing they may touch.
 */
class TelegramTools(
    private val authenticate: () -> Authenticate,
    private val listDialogs: () -> ListDialogs,
    private val readHistory: () -> ReadHistory,
    private val searchMessages: () -> SearchMessages,
    private val contacts: () -> Contacts,
    private val chatMembers: () -> ChatMembers,
    private val listFolders: () -> ListFolders,
    private val messageWriter: () -> MessageWriter,
    private val adminRights: () -> AdminRights,
    private val sessions: () -> Sessions,
    private val listen: () -> Listen,
    private val sendMedia: () -> SendMedia,
    private val downloadMedia: () -> DownloadMedia,
    private val clock: Clock,
) {
    fun all(files: FilesSandbox? = null): List<McpTool> =
        reading() + writing() + destructive() + files?.let(::fileTools).orEmpty()

    private fun reading(): List<McpTool> = listOf(
        WhoamiTool(authenticate),
        ListChatsTool(listDialogs),
        ReadHistoryTool(readHistory),
        SearchMessagesTool(searchMessages),
        CountMessagesTool(searchMessages),
        GetPinnedMessageTool(messageWriter),
        ListFilesTool(searchMessages),
        ListContactsTool(contacts),
        SearchContactsTool(contacts),
        ListBlockedTool(contacts),
        ListMembersTool(chatMembers),
        GetMemberRightsTool(adminRights),
        ListFoldersTool(listFolders),
        ListSessionsTool(sessions),
        WaitForMessageTool(listen, clock),
    )

    private fun writing(): List<McpTool> = listOf(
        SendMessageTool(messageWriter),
        EditMessageTool(messageWriter),
        ForwardMessagesTool(messageWriter),
        MarkReadTool(messageWriter),
        ReactTool(messageWriter),
        RemoveReactionTool(messageWriter),
        PinMessageTool(messageWriter),
        UnpinMessageTool(messageWriter),
        SendChatActionTool(messageWriter),
        InviteMemberTool(chatMembers),
        ImportContactTool(contacts),
    )

    private fun destructive(): List<McpTool> = listOf(
        DeleteMessagesTool(messageWriter),
        UnpinAllMessagesTool(messageWriter),
        KickMemberTool(chatMembers),
        RestrictMemberTool(adminRights, clock),
        SetAdminRightsTool(adminRights),
        BlockContactTool(contacts),
        UnblockContactTool(contacts),
        DeleteContactTool(contacts),
        TerminateSessionTool(sessions),
        TerminateAllSessionsTool(sessions),
    )

    private fun fileTools(sandbox: FilesSandbox): List<McpTool> = listOf(
        SendFileTool(sendMedia, sandbox),
        SendAlbumTool(sendMedia, sandbox),
        DownloadMediaTool(downloadMedia, sandbox),
    )
}
