package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.renderChats
import org.kotlogramme.cli.adapter.format.renderFolders
import org.kotlogramme.cli.adapter.format.renderParticipants
import org.kotlogramme.cli.adapter.mcp.tool.ChatParameter
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.ReadingTool
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.spi.Output

class ListChatsTool(private val listDialogs: () -> ListDialogs) : ReadingTool(
    name = "telegram_list_chats",
    title = "List chats",
    description = "Lists the account's chats (private chats, groups, channels), most recently active first, with " +
        "id, title, @username, unread count and a preview of the last message. The id is what the other tools " +
        "take as 'chat'. When the page is full the result has 'next_cursor'; pass it back as 'cursor' for more.",
    parameters = PARAMETERS,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val limit = arguments.int("limit")
        val chats = listDialogs().list(limit, arguments.stringOrNull("cursor"), all = false)
        output.renderChats(chats)
        // Saved Messages is a synthetic first row; it counts for neither the page size nor the cursor.
        val dialogs = chats.filterNot { it.isSelf }
        if (dialogs.size >= limit) output.nextPage(ListingCursor.formatDialogs(dialogs.last()))
    }

    private companion object {
        val PARAMETERS = ToolParameters(
            IntParameter("limit", "How many chats to return.", min = 1, max = 100, default = 20),
            StringParameter("cursor", "The 'next_cursor' of the previous page, to continue after it."),
        )
    }
}

class ListFoldersTool(private val listFolders: () -> ListFolders) : ReadingTool(
    name = "telegram_list_folders",
    title = "List chat folders",
    description = "Lists the account's chat folders with the chats pinned to, included in and excluded from each.",
    parameters = ToolParameters(),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        output.renderFolders(listFolders().list())
    }
}

class ListMembersTool(private val chatMembers: () -> ChatMembers) : ReadingTool(
    name = "telegram_list_members",
    title = "List chat members",
    description = "Lists the members of a group or channel with id, name, @username and role. When the page is " +
        "full the result has 'next_cursor'; pass it back as 'cursor' for more.",
    parameters = PARAMETERS,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val limit = arguments.int("limit")
        val cursor = arguments.stringOrNull("cursor")
        val members = chatMembers().list(arguments.string("chat"), limit, cursor)
        output.renderParticipants(members)
        if (members.size == limit) {
            val offset = cursor?.let(ListingCursor::parseDecimal) ?: 0
            output.nextPage((offset + members.size).toString())
        }
    }

    private companion object {
        val PARAMETERS = ToolParameters(
            ChatParameter("chat"),
            IntParameter("limit", "How many members to return.", min = 1, max = 200, default = 50),
            StringParameter("cursor", "The 'next_cursor' of the previous page, to continue after it."),
        )
    }
}
