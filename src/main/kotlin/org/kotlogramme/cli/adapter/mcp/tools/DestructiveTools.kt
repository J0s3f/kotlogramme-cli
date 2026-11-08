package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.cli.RightsOptions
import org.kotlogramme.cli.adapter.mcp.tool.BooleanParameter
import org.kotlogramme.cli.adapter.mcp.tool.ChatParameter
import org.kotlogramme.cli.adapter.mcp.tool.ChoiceListParameter
import org.kotlogramme.cli.adapter.mcp.tool.DestructiveTool
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.adapter.mcp.tool.UserParameter
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.spi.Clock
import org.kotlogramme.cli.application.port.spi.Output
import java.time.Duration

class DeleteMessagesTool(private val messageWriter: () -> MessageWriter) : DestructiveTool(
    name = "telegram_delete_messages",
    title = "Delete messages",
    description = "Permanently deletes messages from a chat, for everyone where Telegram allows it. It cannot be " +
        "undone. Check the ids with telegram_read_history first.",
    parameters = ToolParameters(
        ChatParameter("chat"),
        messageIdsParameter("The ids of the messages to delete, at most $MAX_MESSAGES_PER_CALL."),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val deleted = messageWriter().delete(arguments.string("chat"), arguments.ints("message_ids"))
        output.message("Deleted $deleted message(s).")
        output.count(deleted)
    }
}

class UnpinAllMessagesTool(private val messageWriter: () -> MessageWriter) : DestructiveTool(
    name = "telegram_unpin_all_messages",
    title = "Unpin every message",
    description = "Removes every pinned message from a chat at once. To unpin a single message use " +
        "telegram_unpin_message.",
    parameters = ToolParameters(ChatParameter("chat")),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        messageWriter().unpinAll(chat)
        output.message("Unpinned every message in $chat.")
    }
}

class KickMemberTool(private val chatMembers: () -> ChatMembers) : DestructiveTool(
    name = "telegram_kick_member",
    title = "Remove a member",
    description = "Removes a person from a group or channel, which needs the right to ban users there.",
    parameters = ToolParameters(ChatParameter("chat"), UserParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val user = arguments.string("user")
        chatMembers().kick(chat, user)
        output.message("Kicked $user from $chat.")
    }
}

class RestrictMemberTool(
    private val adminRights: () -> AdminRights,
    private val clock: Clock,
) : DestructiveTool(
    name = "telegram_restrict_member",
    title = "Restrict a member",
    description = "Bans or restricts a member of a group or channel. The abilities named in 'allow' are kept and " +
        "everything else is taken away, so an empty 'allow' is a full ban. The restriction lasts " +
        "'hours' hours, or until someone lifts it when 'forever' is true.",
    parameters = PARAMETERS,
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val user = arguments.string("user")
        val forever = arguments.boolean("forever")
        val hours = arguments.int("hours")
        val until = if (forever) null else clock.now().plus(Duration.ofHours(hours.toLong()))
        val allowed = RightsOptions.select(arguments.strings("allow"), RightsOptions.RESTRICTION_NAMES)
        adminRights().restrict(chat, user, RightsOptions.restrictions(allowed, until))
        output.message(if (forever) "Restricted $user in $chat, forever." else "Restricted $user in $chat for ${hours}h.")
    }

    private companion object {
        const val DEFAULT_HOURS = 24
        const val MAX_HOURS = 24 * 366

        val PARAMETERS = ToolParameters(
            ChatParameter("chat"),
            UserParameter(),
            ChoiceListParameter(
                "allow",
                "The abilities the member keeps. Leave it empty to take everything away.",
                RightsOptions.RESTRICTION_NAMES,
            ),
            IntParameter(
                "hours",
                "How long the restriction lasts.",
                min = 1,
                max = MAX_HOURS,
                default = DEFAULT_HOURS,
            ),
            BooleanParameter("forever", "Never lift the restriction; 'hours' is then ignored.", default = false),
        )
    }
}

class SetAdminRightsTool(private val adminRights: () -> AdminRights) : DestructiveTool(
    name = "telegram_set_admin_rights",
    title = "Set a member's admin rights",
    description = "Makes a member an admin with exactly the rights named in 'grant'. It replaces the rights the " +
        "member has: an empty 'grant' removes their admin rights. Granting add-admins lets them appoint more admins.",
    parameters = ToolParameters(
        ChatParameter("chat"),
        UserParameter(),
        ChoiceListParameter("grant", "The rights to grant.", RightsOptions.ADMIN_NAMES, required = true),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val user = arguments.string("user")
        val granted = RightsOptions.select(arguments.strings("grant"), RightsOptions.ADMIN_NAMES)
        adminRights().promote(chat, user, RightsOptions.rights(granted))
        output.message("Set ${granted.size} admin right(s) for $user in $chat.")
    }
}

class BlockContactTool(private val contacts: () -> Contacts) : DestructiveTool(
    name = "telegram_block_user",
    title = "Block a person",
    description = "Blocks a person, so they can no longer message the user's account.",
    parameters = ToolParameters(UserParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val user = arguments.string("user")
        contacts().block(user)
        output.message("Blocked $user.")
    }
}

class UnblockContactTool(private val contacts: () -> Contacts) : DestructiveTool(
    name = "telegram_unblock_user",
    title = "Unblock a person",
    description = "Lets a blocked person message the user's account again.",
    parameters = ToolParameters(UserParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val user = arguments.string("user")
        contacts().unblock(user)
        output.message("Unblocked $user.")
    }
}

class DeleteContactTool(private val contacts: () -> Contacts) : DestructiveTool(
    name = "telegram_delete_contact",
    title = "Delete a contact",
    description = "Removes a person from the saved contacts. The person stays reachable and is not blocked.",
    parameters = ToolParameters(UserParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val user = arguments.string("user")
        contacts().delete(user)
        output.message("Deleted $user from contacts. The person is not blocked.")
    }
}

class TerminateSessionTool(private val sessions: () -> Sessions) : DestructiveTool(
    name = "telegram_terminate_session",
    title = "End a login session",
    description = "Logs one device out of the user's account. Take the hash from telegram_list_sessions; it never " +
        "ends the session this server runs in.",
    parameters = ToolParameters(
        StringParameter("session_hash", "The 'hash' of the session as telegram_list_sessions shows it.", required = true),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val text = arguments.string("session_hash").trim()
        val hash = text.toLongOrNull()
            ?: throw IllegalArgumentException("The argument 'session_hash' must be a number as listed, not '$text'.")
        sessions().terminate(hash)
        output.message("Terminated session $hash.")
    }
}

class TerminateAllSessionsTool(private val sessions: () -> Sessions) : DestructiveTool(
    name = "telegram_terminate_all_sessions",
    title = "End every other login session",
    description = "Logs every device out of the user's account except the session this server runs in. It " +
        "cannot be undone, so it needs 'confirm' to be true.",
    parameters = ToolParameters(
        BooleanParameter("confirm", "Must be true: the user wants every other session ended.", required = true),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        require(arguments.boolean("confirm")) {
            "This ends every session except the current one and cannot be undone. Call again with confirm true " +
                "once the user has agreed."
        }
        sessions().terminateAll()
        output.message("Terminated every session except the current one.")
    }
}
