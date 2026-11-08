package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A reference to a chat that already exists: `me`, an `@username` or a numeric id.
 *
 * Links are refused on purpose. The code that turns a chat reference into a peer accepts an invite link
 * and joins the chat it points to, which a tool that only reads must never do.
 */
class ChatParameter(
    name: String,
    description: String = DEFAULT_DESCRIPTION,
    override val required: Boolean = true,
) : Parameter(name, description) {
    override val default: Any? = null

    override fun schema() = described("string")

    override fun parse(raw: JsonElement): Any {
        val primitive = raw as? JsonPrimitive
        if (primitive == null || raw is JsonNull || !primitive.isString) mismatch("a string")
        val reference = primitive.content.trim()
        require(ACCEPTED.matches(reference)) {
            "The argument '$name' must be 'me', an @username or a numeric chat id, not '$reference'. " +
                "Links are not accepted: opening an invite link joins the chat."
        }
        return reference
    }

    companion object {
        const val DEFAULT_DESCRIPTION =
            "The chat: 'me' for Saved Messages, an @username, or a numeric chat id as listed by " +
                "telegram_list_chats. Invite links are not accepted."

        private val ACCEPTED = Regex("(?i)@?me|@[A-Za-z][A-Za-z0-9_]{2,31}|-?[0-9]{1,20}")
    }
}
