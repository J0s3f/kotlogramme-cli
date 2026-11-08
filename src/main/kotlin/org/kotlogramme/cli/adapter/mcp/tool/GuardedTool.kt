package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonObject

/**
 * Turns the failures a person can act on into tool errors the model can read.
 *
 * Telegram rejecting a request or the credentials being unset are results of the call, not bugs, so they
 * are reported inside the result. [explain] returns the text for such a failure, or `null` for anything
 * else, which then stays an internal error.
 */
class GuardedTool(
    private val tool: McpTool,
    private val explain: (Exception) -> String?,
) : McpTool by tool {
    override fun call(arguments: JsonObject, context: ToolContext): ToolResult =
        try {
            tool.call(arguments, context)
        } catch (failure: IllegalArgumentException) {
            throw failure
        } catch (failure: Exception) {
            ToolResult.error(explain(failure) ?: throw failure)
        }
}
