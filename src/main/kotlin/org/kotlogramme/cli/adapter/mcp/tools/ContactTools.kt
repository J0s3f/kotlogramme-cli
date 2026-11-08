package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.ReadingTool
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.spi.Output

class ListContactsTool(private val contacts: () -> Contacts) : ReadingTool(
    name = "telegram_list_contacts",
    title = "List contacts",
    description = "Lists the account's saved contacts with id, name, @username and phone number.",
    parameters = ToolParameters(
        IntParameter("limit", "How many contacts to return.", min = 1, max = 200, default = 50),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        output.renderContacts(contacts().list(arguments.int("limit")))
    }
}

class SearchContactsTool(private val contacts: () -> Contacts) : ReadingTool(
    name = "telegram_search_contacts",
    title = "Search contacts",
    description = "Searches the saved contacts and Telegram's public directory by name or @username.",
    parameters = ToolParameters(
        StringParameter("query", "The name or @username to look for.", required = true),
        IntParameter("limit", "How many matches to return.", min = 1, max = 200, default = 50),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        output.renderContacts(contacts().search(arguments.string("query"), arguments.int("limit")))
    }
}
