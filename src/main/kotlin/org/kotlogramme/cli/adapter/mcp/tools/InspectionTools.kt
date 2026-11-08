package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.renderBlockedContacts
import org.kotlogramme.cli.adapter.format.renderChatRights
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.adapter.format.renderSessions
import org.kotlogramme.cli.adapter.mcp.tool.ChatParameter
import org.kotlogramme.cli.adapter.mcp.tool.ChoiceParameter
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.ReadingTool
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.adapter.mcp.tool.UserParameter
import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.MediaFileKind

class ListSessionsTool(private val sessions: () -> Sessions) : ReadingTool(
    name = "telegram_list_sessions",
    title = "List login sessions",
    description = "Lists the devices logged in to the user's account with their 'hash', device, app, IP address, " +
        "country and when they started. The current one, which this server uses, is marked.",
    parameters = ToolParameters(),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        output.renderSessions(sessions().list())
    }
}

class ListBlockedTool(private val contacts: () -> Contacts) : ReadingTool(
    name = "telegram_list_blocked",
    title = "List blocked people",
    description = "Lists the people the user has blocked, newest block first. When the page is full the result " +
        "has 'next_cursor'; pass it back as 'cursor' for more.",
    parameters = PARAMETERS,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val limit = arguments.int("limit")
        val cursor = arguments.stringOrNull("cursor")
        val blocked = contacts().blocked(limit, cursor)
        output.renderBlockedContacts(blocked)
        if (blocked.size == limit) {
            val offset = cursor?.let(ListingCursor::parseDecimal) ?: 0
            output.nextPage((offset + blocked.size).toString())
        }
    }

    private companion object {
        val PARAMETERS = ToolParameters(
            IntParameter("limit", "How many people to return.", min = 1, max = 100, default = 50),
            StringParameter("cursor", "The 'next_cursor' of the previous page, to continue after it."),
        )
    }
}

class GetMemberRightsTool(private val adminRights: () -> AdminRights) : ReadingTool(
    name = "telegram_get_member_rights",
    title = "Show a member's admin rights",
    description = "Shows which admin rights a member of a group or channel holds and which they lack.",
    parameters = ToolParameters(ChatParameter("chat"), UserParameter()),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        output.renderChatRights(adminRights().permissions(arguments.string("chat"), arguments.string("user")))
    }
}

class ListFilesTool(private val searchMessages: () -> SearchMessages) : ReadingTool(
    name = "telegram_list_files",
    title = "List files in a chat",
    description = "Lists the messages of a chat that carry media or files, newest first, with message id, kind, " +
        "size and file name. The message id is what telegram_download_media takes. When the page is full the " +
        "result has 'next_cursor'; pass it back as 'cursor' for more.",
    parameters = PARAMETERS,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val limit = arguments.int("limit")
        val kind = MediaFileKind.entries.first { it.cliName == arguments.string("kind") }
        val files = searchMessages().files(arguments.string("chat"), kind, limit, arguments.stringOrNull("cursor"))
        output.renderFiles(files)
        if (files.size == limit) output.nextPage(files.last().id.toString())
    }

    private companion object {
        val PARAMETERS = ToolParameters(
            ChatParameter("chat"),
            ChoiceParameter(
                "kind",
                "Which files to list.",
                MediaFileKind.entries.map { it.cliName },
                default = MediaFileKind.ALL.cliName,
            ),
            IntParameter("limit", "How many files to return.", min = 1, max = 100, default = 20),
            StringParameter("cursor", "The 'next_cursor' of the previous page, to continue after it."),
        )
    }
}
