package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.renderAccount
import org.kotlogramme.cli.adapter.mcp.tool.ReadingTool
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolFailure
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.spi.Output

class WhoamiTool(private val authenticate: () -> Authenticate) : ReadingTool(
    name = "telegram_whoami",
    title = "Telegram account",
    description = "Shows which Telegram account this server is signed in as: display name, numeric id, " +
        "username and phone number. Call it first to check that the connection works.",
    parameters = ToolParameters(),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        when (val status = authenticate().status()) {
            is AccountStatus.SignedIn -> output.renderAccount(status.account)
            is AccountStatus.Anonymous -> throw ToolFailure(
                "Not signed in. Ask the user to run `kotlogramme login` once in a terminal, then restart this server.",
            )
        }
    }
}
