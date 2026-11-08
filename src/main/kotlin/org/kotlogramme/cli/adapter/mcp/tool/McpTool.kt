package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Something the model can invoke.
 *
 * A tool validates its own arguments and answers domain failures with [ToolResult.error] or by throwing
 * [IllegalArgumentException], which the server turns into the same: the specification wants those
 * reported inside the result so the model can correct itself, not as protocol errors.
 */
interface McpTool {
    /** 1 to 128 characters from `A-Z a-z 0-9 _ - .`, unique within the server. */
    val name: String
    val title: String
    val description: String

    /** A JSON Schema object for the arguments; a tool without parameters uses `additionalProperties: false`. */
    val inputSchema: JsonObject
    val annotations: ToolAnnotations

    fun call(arguments: JsonObject, context: ToolContext): ToolResult
}

/**
 * What a client may assume about a tool without calling it.
 *
 * These are hints and clients treat them as untrusted; they are not an access control. All four are always
 * sent, because the protocol's defaults (destructive and open world unless said otherwise) are the opposite
 * of what a read-only tool should claim.
 */
data class ToolAnnotations(
    val readOnly: Boolean,
    val destructive: Boolean = false,
    val idempotent: Boolean = readOnly,
    val openWorld: Boolean = true,
)

/** What the transport tells a running call, so a long call can stop when the client cancels its request. */
data class ToolContext(val isCancelled: () -> Boolean = { false })

/** The outcome of a call: text for every client and, when the data has a shape, the same data structured. */
data class ToolResult(
    val text: String,
    val structured: JsonElement? = null,
    val isError: Boolean = false,
) {
    companion object {
        fun text(text: String) = ToolResult(text)

        /** [json] is shown as text and also returned parsed, as the specification recommends for compatibility. */
        fun json(json: String): ToolResult {
            val parsed = try {
                Json.parseToJsonElement(json)
            } catch (error: SerializationException) {
                throw IllegalArgumentException("A tool produced text that is not JSON: ${error.message}", error)
            }
            return ToolResult(json, parsed)
        }

        fun error(message: String) = ToolResult(message, isError = true)
    }
}
