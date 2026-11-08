package org.kotlogramme.cli.adapter.mcp.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A JSON-RPC error: the [code], a [message] a person can read, and optional structured [data]. */
data class JsonRpcError(val code: Int, val message: String, val data: JsonElement? = null)

/**
 * One line a client sent, classified.
 *
 * A server only ever receives requests and notifications. Anything else, including a response, a batch
 * or a request with an unusable id, is [Invalid] and is answered with an error rather than dropped, so a
 * client never waits for a reply that will not come.
 */
sealed interface JsonRpcMessage {
    data class Request(val id: JsonElement, val method: String, val params: JsonObject?) : JsonRpcMessage

    data class Notification(val method: String, val params: JsonObject?) : JsonRpcMessage

    /** [id] is the id of the offending request when it could be read, otherwise `null`. */
    data class Invalid(val id: JsonElement?, val error: JsonRpcError) : JsonRpcMessage

    companion object {
        private const val JSONRPC_VERSION = "2.0"

        fun parse(line: String): JsonRpcMessage {
            val element = try {
                Json.parseToJsonElement(line)
            } catch (error: SerializationException) {
                // The text is not echoed back: it is the client's input and can be large.
                return Invalid(null, JsonRpcError(McpErrorCodes.PARSE_ERROR, "Parse error: the message is not valid JSON"))
            }
            val envelope = element as? JsonObject
                ?: return invalidRequest(null, "A message must be a single JSON object; batches are not supported")
            return classify(envelope)
        }

        private fun classify(envelope: JsonObject): JsonRpcMessage {
            val id = envelope["id"]
            if (id != null && !isValidId(id)) {
                return invalidRequest(null, "The id must be a string or an integer")
            }
            if ((envelope["jsonrpc"] as? JsonPrimitive)?.takeIf { it.isString }?.content != JSONRPC_VERSION) {
                return invalidRequest(id, "The jsonrpc member must be \"$JSONRPC_VERSION\"")
            }
            val method = (envelope["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return invalidRequest(id, "The message has no method; the server only accepts requests")
            val params = when (val raw = envelope["params"]) {
                null, JsonNull -> null
                is JsonObject -> raw
                else -> return Invalid(
                    id,
                    JsonRpcError(McpErrorCodes.INVALID_PARAMS, "The params must be an object"),
                )
            }
            return if (id == null) Notification(method, params) else Request(id, method, params)
        }

        private fun isValidId(id: JsonElement): Boolean {
            if (id !is JsonPrimitive || id is JsonNull) return false
            return id.isString || id.longOrNull != null
        }

        private fun invalidRequest(id: JsonElement?, message: String) =
            Invalid(id, JsonRpcError(McpErrorCodes.INVALID_REQUEST, message))
    }
}

/** Builders for the two response shapes, so no caller assembles an envelope by hand. */
object JsonRpcResponses {
    fun result(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    /**
     * An error response. When the request id could not be read, [id] is `null` and the member is left out:
     * MCP's schema makes `id` optional on an error and does not allow JSON-RPC's `null`.
     */
    fun error(id: JsonElement?, error: JsonRpcError): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        id?.let { put("id", it) }
        put(
            "error",
            buildJsonObject {
                put("code", error.code)
                put("message", error.message)
                error.data?.let { put("data", it) }
            },
        )
    }
}
