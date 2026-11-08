package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A reference to another account: an `@username` or a numeric user id.
 *
 * Like [ChatParameter] it refuses links, and it also refuses `me`: a tool that kicks, blocks or restricts
 * someone should never be able to turn that on the account it runs as.
 */
class UserParameter(
    name: String = "user",
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
            "The argument '$name' must be an @username or a numeric user id, not '$reference'. " +
                "Links and 'me' are not accepted."
        }
        return reference
    }

    companion object {
        const val DEFAULT_DESCRIPTION =
            "The person: an @username or a numeric user id as listed by telegram_list_members or " +
                "telegram_list_contacts. Invite links and 'me' are not accepted."

        private val ACCEPTED = Regex("@[A-Za-z][A-Za-z0-9_]{2,31}|[0-9]{1,20}")
    }
}
