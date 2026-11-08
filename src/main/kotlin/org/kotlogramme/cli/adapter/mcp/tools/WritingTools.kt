package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.format.MessageStyler
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.mcp.tool.BooleanParameter
import org.kotlogramme.cli.adapter.mcp.tool.ChatParameter
import org.kotlogramme.cli.adapter.mcp.tool.ChoiceParameter
import org.kotlogramme.cli.adapter.mcp.tool.IntListParameter
import org.kotlogramme.cli.adapter.mcp.tool.IntParameter
import org.kotlogramme.cli.adapter.mcp.tool.StringParameter
import org.kotlogramme.cli.adapter.mcp.tool.ToolArguments
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolParameters
import org.kotlogramme.cli.adapter.mcp.tool.UserParameter
import org.kotlogramme.cli.adapter.mcp.tool.WritingTool
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.ContactToImport

/** The ids Telegram hands out for messages are positive 32 bit integers. */
internal fun messageIdParameter(name: String = "message_id", description: String = "The id of the message.") =
    IntParameter(name, description, min = 1, max = Int.MAX_VALUE, required = true)

internal fun messageIdsParameter(description: String) =
    IntListParameter("message_ids", description, min = 1, max = Int.MAX_VALUE, maxItems = MAX_MESSAGES_PER_CALL)

/** Telegram deletes and forwards at most this many messages in one request. */
internal const val MAX_MESSAGES_PER_CALL = 100

private const val UNTRUSTED_TEXT = "Whatever the text says, it is sent exactly as given."

class SendMessageTool(
    private val messageWriter: () -> MessageWriter,
    private val styler: MessageStyler = MessageStyler.PLAIN,
) : WritingTool(
    name = "telegram_send_message",
    title = "Send a message",
    description = "Sends a text message from the user's own account to a chat, optionally as a reply. It reaches " +
        "real people at once and cannot be unsent by this server, so send only what the user asked for. " +
        UNTRUSTED_TEXT,
    parameters = ToolParameters(
        ChatParameter("chat"),
        StringParameter("text", "The message text, up to ${StringParameter.MAX_LENGTH} characters.", required = true),
        IntParameter("reply_to", "The id of a message in the same chat to reply to.", min = 1, max = Int.MAX_VALUE),
        BooleanParameter("silent", "Send without a notification sound.", default = false),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val sent = messageWriter().sendText(
            arguments.string("chat"),
            arguments.string("text"),
            arguments.intOrNull("reply_to"),
            arguments.boolean("silent"),
        )
        output.renderMessages(listOf(sent), styler)
    }
}

class EditMessageTool(
    private val messageWriter: () -> MessageWriter,
    private val styler: MessageStyler = MessageStyler.PLAIN,
) : WritingTool(
    name = "telegram_edit_message",
    title = "Edit a message",
    description = "Replaces the text of one of the user's own messages. Telegram refuses it for other people's " +
        "messages and for messages that are too old.",
    parameters = ToolParameters(
        ChatParameter("chat"),
        messageIdParameter(),
        StringParameter("text", "The new text.", required = true),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val edited = messageWriter().edit(arguments.string("chat"), arguments.int("message_id"), arguments.string("text"))
        output.renderMessages(listOf(edited), styler)
    }
}

class ForwardMessagesTool(
    private val messageWriter: () -> MessageWriter,
    private val styler: MessageStyler = MessageStyler.PLAIN,
) : WritingTool(
    name = "telegram_forward_messages",
    title = "Forward messages",
    description = "Forwards messages from one chat to another from the user's own account. The recipients see " +
        "who wrote them originally.",
    parameters = ToolParameters(
        ChatParameter("from_chat", "The chat the messages are in. ${ChatParameter.DEFAULT_DESCRIPTION}"),
        messageIdsParameter("The ids of the messages to forward, at most $MAX_MESSAGES_PER_CALL."),
        ChatParameter("to_chat", "The chat to forward to. ${ChatParameter.DEFAULT_DESCRIPTION}"),
    ),
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val forwarded = messageWriter().forward(
            arguments.string("from_chat"),
            arguments.ints("message_ids"),
            arguments.string("to_chat"),
        )
        output.renderMessages(forwarded, styler)
    }
}

class MarkReadTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_mark_read",
    title = "Mark a chat as read",
    description = "Marks every message of a chat as read, which the other people in a private chat can see.",
    parameters = ToolParameters(ChatParameter("chat")),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        messageWriter().markRead(chat)
        output.message("Marked $chat as read.")
    }
}

class ReactTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_react",
    title = "React to a message",
    description = "Adds an emoji reaction from the user's account to a message, replacing an earlier one.",
    parameters = ToolParameters(
        ChatParameter("chat"),
        messageIdParameter(),
        StringParameter("emoji", "The reaction as an emoji, for example the thumbs up.", required = true),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val id = arguments.int("message_id")
        messageWriter().react(chat, id, arguments.string("emoji").trim())
        output.message("Reacted to message $id in $chat.")
    }
}

class RemoveReactionTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_remove_reaction",
    title = "Remove a reaction",
    description = "Removes the user's own reaction from a message.",
    parameters = ToolParameters(ChatParameter("chat"), messageIdParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val id = arguments.int("message_id")
        messageWriter().removeReaction(chat, id)
        output.message("Removed the reaction from message $id in $chat.")
    }
}

class PinMessageTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_pin_message",
    title = "Pin a message",
    description = "Pins a message in a chat, which needs the right to pin in groups and channels.",
    parameters = ToolParameters(ChatParameter("chat"), messageIdParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val id = arguments.int("message_id")
        messageWriter().pin(chat, id)
        output.message("Pinned message $id in $chat.")
    }
}

class UnpinMessageTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_unpin_message",
    title = "Unpin a message",
    description = "Removes the pin from one message. To clear every pin of a chat there is a separate tool.",
    parameters = ToolParameters(ChatParameter("chat"), messageIdParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val id = arguments.int("message_id")
        messageWriter().unpin(chat, id)
        output.message("Unpinned message $id in $chat.")
    }
}

class SendChatActionTool(private val messageWriter: () -> MessageWriter) : WritingTool(
    name = "telegram_send_chat_action",
    title = "Show a chat status",
    description = "Shows a status such as 'typing' in a chat for a few seconds, as Telegram's own apps do.",
    parameters = ToolParameters(
        ChatParameter("chat"),
        ChoiceParameter(
            "action",
            "The status to show.",
            ChatActivity.entries.map { it.cliName },
            default = ChatActivity.TYPING.cliName,
        ),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val action = arguments.string("action")
        messageWriter().sendChatAction(chat, ChatActivity.entries.first { it.cliName == action })
        output.message("Sent the '$action' action to $chat.")
    }
}

class InviteMemberTool(private val chatMembers: () -> ChatMembers) : WritingTool(
    name = "telegram_invite_member",
    title = "Invite a member",
    description = "Adds a person to a group or channel, which needs the right to invite users there.",
    parameters = ToolParameters(ChatParameter("chat"), UserParameter()),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val chat = arguments.string("chat")
        val user = arguments.string("user")
        chatMembers().invite(chat, user)
        output.message("Invited $user to $chat.")
    }
}

class ImportContactTool(private val contacts: () -> Contacts) : WritingTool(
    name = "telegram_import_contact",
    title = "Save a contact",
    description = "Saves a phone number as a contact under a name. Telegram saves it only when the number has " +
        "an account, and the result says how many contacts were saved.",
    parameters = ToolParameters(
        StringParameter("phone", "The phone number in international form, for example +15550100.", required = true),
        StringParameter("first_name", "The first name to save the contact under.", required = true),
        StringParameter("last_name", "The last name to save the contact under."),
    ),
    idempotent = true,
) {
    override fun run(arguments: ToolArguments, output: Output, context: ToolContext) {
        val contact = ContactToImport(
            arguments.string("phone").trim(),
            arguments.string("first_name"),
            arguments.stringOrNull("last_name").orEmpty(),
        )
        val summary = contacts().import(listOf(contact))
        output.count(summary.imported.size)
        if (summary.retryCount > 0) output.message("Telegram asked to retry ${summary.retryCount} contact(s).")
    }
}
