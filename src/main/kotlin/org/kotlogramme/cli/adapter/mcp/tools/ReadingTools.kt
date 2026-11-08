package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.spi.Clock

/**
 * The tools that only look at the account.
 *
 * Every use case is a provider, because building one opens a Telegram connection and a server that is
 * only asked to list tools should not do that.
 */
class ReadingTools(
    private val authenticate: () -> Authenticate,
    private val listDialogs: () -> ListDialogs,
    private val readHistory: () -> ReadHistory,
    private val searchMessages: () -> SearchMessages,
    private val contacts: () -> Contacts,
    private val chatMembers: () -> ChatMembers,
    private val listFolders: () -> ListFolders,
    private val messageWriter: () -> MessageWriter,
    private val listen: () -> Listen,
    private val clock: Clock,
) {
    fun all(): List<McpTool> = listOf(
        WhoamiTool(authenticate),
        ListChatsTool(listDialogs),
        ReadHistoryTool(readHistory),
        SearchMessagesTool(searchMessages),
        CountMessagesTool(searchMessages),
        GetPinnedMessageTool(messageWriter),
        ListContactsTool(contacts),
        SearchContactsTool(contacts),
        ListMembersTool(chatMembers),
        ListFoldersTool(listFolders),
        WaitForMessageTool(listen, clock),
    )
}
