package org.kotlogramme.cli.adapter.mcp.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcError
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcMessage
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcResponses
import org.kotlogramme.cli.adapter.mcp.protocol.McpErrorCodes
import org.kotlogramme.cli.adapter.mcp.protocol.MetaKeys
import org.kotlogramme.cli.adapter.mcp.protocol.ProtocolVersions
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.adapter.mcp.tool.ToolCatalog
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult

/** Who the server says it is, in discovery and in the handshake. */
data class ServerIdentity(val name: String, val title: String, val version: String)

/** Whether the client has cancelled a request; the transport knows, the server only asks. */
fun interface CancellationSignal {
    fun isCancelled(requestId: JsonElement): Boolean

    companion object {
        val NEVER = CancellationSignal { false }
    }
}

/**
 * The MCP protocol for a tools-only server, with no IO and no threads: a message goes in, a response comes
 * out, which is what makes the whole protocol testable without a process.
 *
 * Two eras are served from one process, as the specification allows. A request that carries the stateless
 * revision's per-request `_meta` is answered statelessly. An `initialize` request selects the handshake
 * revisions for the rest of the process, and requests without that `_meta` are then answered in their
 * shape. Specification: https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning
 *
 * The server holds state only for the handshake revisions, which are stateful by definition; it must be
 * driven from one thread at a time.
 */
class McpServer(
    private val identity: ServerIdentity,
    private val instructions: String,
    private val tools: ToolCatalog,
    private val log: (String) -> Unit = { System.err.println(it) },
) {
    private var handshakeVersion: String? = null

    /** The text form, for callers that deal in lines. */
    fun handle(line: String): String? = handle(JsonRpcMessage.parse(line))?.toString()

    /** Answers [message], or returns `null` when it is a notification, which never gets a response. */
    fun handle(message: JsonRpcMessage, cancelled: CancellationSignal = CancellationSignal.NEVER): JsonObject? =
        when (message) {
            is JsonRpcMessage.Invalid -> JsonRpcResponses.error(message.id, message.error)
            is JsonRpcMessage.Notification -> null
            is JsonRpcMessage.Request -> answer(message, cancelled)
        }

    private fun answer(request: JsonRpcMessage.Request, cancelled: CancellationSignal): JsonObject =
        try {
            JsonRpcResponses.result(request.id, dispatch(request, cancelled))
        } catch (failure: ProtocolFailure) {
            JsonRpcResponses.error(request.id, failure.error)
        } catch (failure: Exception) {
            log("Internal error handling ${request.method}: ${failure.stackTraceToString()}")
            JsonRpcResponses.error(request.id, JsonRpcError(McpErrorCodes.INTERNAL_ERROR, "Internal error"))
        }

    private fun dispatch(request: JsonRpcMessage.Request, cancelled: CancellationSignal): JsonObject =
        when (request.method) {
            "initialize" -> initialize(request)
            "ping" -> ping(request)
            "server/discover" -> discover(request)
            "tools/list" -> toolsList(request)
            "tools/call" -> toolsCall(request, cancelled)
            else -> throw ProtocolFailure(McpErrorCodes.METHOD_NOT_FOUND, "Method not found: ${request.method}")
        }

    private fun initialize(request: JsonRpcMessage.Request): JsonObject {
        val requested = (request.params?.get("protocolVersion") as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalidParams("initialize needs a protocolVersion")
        val chosen = requested.takeIf { it in ProtocolVersions.LEGACY } ?: ProtocolVersions.LATEST_LEGACY
        handshakeVersion = chosen
        return buildJsonObject {
            put("protocolVersion", chosen)
            put("capabilities", capabilities())
            put("serverInfo", serverInfo())
            put("instructions", instructions)
        }
    }

    /** `ping` belongs to the handshake revisions; the stateless one removed it. */
    private fun ping(request: JsonRpcMessage.Request): JsonObject {
        if (carriesStatelessMeta(request)) {
            throw ProtocolFailure(McpErrorCodes.METHOD_NOT_FOUND, "Method not found: ping")
        }
        return JsonObject(emptyMap())
    }

    private fun discover(request: JsonRpcMessage.Request): JsonObject {
        requireStateless(request)
        return stateless {
            put("supportedVersions", JsonArray(ProtocolVersions.SUPPORTED.map(::JsonPrimitive)))
            put("capabilities", capabilities())
            put("instructions", instructions)
            putCacheHints()
        }
    }

    private fun toolsList(request: JsonRpcMessage.Request): JsonObject {
        val listing = JsonArray(tools.tools.map(::describe))
        return when (eraOf(request)) {
            Era.STATELESS -> stateless {
                put("tools", listing)
                putCacheHints()
            }
            Era.HANDSHAKE -> buildJsonObject { put("tools", listing) }
        }
    }

    private fun toolsCall(request: JsonRpcMessage.Request, cancelled: CancellationSignal): JsonObject {
        val era = eraOf(request)
        val params = request.params ?: throw invalidParams("tools/call needs params")
        val name = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalidParams("tools/call needs the name of a tool")
        val tool = tools.find(name) ?: throw invalidParams("Unknown tool: $name")
        val arguments = when (val raw = params["arguments"]) {
            null, JsonNull -> JsonObject(emptyMap())
            is JsonObject -> raw
            else -> throw invalidParams("The arguments of $name must be an object")
        }
        val result = run(tool, arguments, ToolContext { cancelled.isCancelled(request.id) })
        val body = describe(result)
        return if (era == Era.STATELESS) stateless { body.forEach { (key, value) -> put(key, value) } } else body
    }

    private fun run(tool: McpTool, arguments: JsonObject, context: ToolContext): ToolResult =
        try {
            tool.call(arguments, context)
        } catch (rejected: IllegalArgumentException) {
            ToolResult.error(rejected.message ?: "Invalid arguments")
        }

    private fun describe(tool: McpTool): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("title", tool.title)
        put("description", tool.description)
        put("inputSchema", tool.inputSchema)
        putJsonObject("annotations") {
            put("readOnlyHint", tool.annotations.readOnly)
            put("destructiveHint", tool.annotations.destructive)
            put("idempotentHint", tool.annotations.idempotent)
            put("openWorldHint", tool.annotations.openWorld)
        }
    }

    private fun describe(result: ToolResult): JsonObject = buildJsonObject {
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", result.text)
                },
            )
        }
        result.structured?.let { put("structuredContent", it) }
        put("isError", result.isError)
    }

    private fun capabilities(): JsonObject = buildJsonObject {
        putJsonObject("tools") { put("listChanged", false) }
    }

    private fun serverInfo(): JsonObject = buildJsonObject {
        put("name", identity.name)
        put("title", identity.title)
        put("version", identity.version)
    }

    /** A result of the stateless revision: marked complete and signed with the server's identity. */
    private fun stateless(body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("resultType", "complete")
        body()
        putJsonObject("_meta") { put(MetaKeys.SERVER_INFO, serverInfo()) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putCacheHints() {
        put("ttlMs", CACHE_TTL_MILLIS)
        put("cacheScope", "public")
    }

    private fun eraOf(request: JsonRpcMessage.Request): Era =
        when {
            carriesStatelessMeta(request) -> {
                requireStateless(request)
                Era.STATELESS
            }
            handshakeVersion != null -> Era.HANDSHAKE
            else -> throw missingMeta()
        }

    private fun carriesStatelessMeta(request: JsonRpcMessage.Request): Boolean =
        statelessMeta(request)?.containsKey(MetaKeys.PROTOCOL_VERSION) == true

    private fun statelessMeta(request: JsonRpcMessage.Request): JsonObject? = request.params?.get("_meta") as? JsonObject

    /** Checks the per-request fields the stateless revision requires on every request. */
    private fun requireStateless(request: JsonRpcMessage.Request) {
        val meta = statelessMeta(request) ?: throw missingMeta()
        val version = (meta[MetaKeys.PROTOCOL_VERSION] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalidParams("_meta.${MetaKeys.PROTOCOL_VERSION} must be a string")
        if (version != ProtocolVersions.MODERN) {
            throw ProtocolFailure(
                McpErrorCodes.UNSUPPORTED_PROTOCOL_VERSION,
                "Unsupported protocol version",
                buildJsonObject {
                    put("supported", JsonArray(ProtocolVersions.SUPPORTED.map(::JsonPrimitive)))
                    put("requested", version)
                },
            )
        }
        if (meta[MetaKeys.CLIENT_CAPABILITIES] !is JsonObject) {
            throw invalidParams("_meta.${MetaKeys.CLIENT_CAPABILITIES} is required")
        }
    }

    private fun missingMeta() = invalidParams(
        "Missing _meta: send ${MetaKeys.PROTOCOL_VERSION} and ${MetaKeys.CLIENT_CAPABILITIES} with every request, " +
            "or open with initialize",
    )

    private fun invalidParams(message: String) = ProtocolFailure(McpErrorCodes.INVALID_PARAMS, message)

    private enum class Era { STATELESS, HANDSHAKE }

    private class ProtocolFailure(code: Int, message: String, data: JsonElement? = null) :
        RuntimeException(message) {
        val error = JsonRpcError(code, message, data)
    }

    private companion object {
        /** How long a client may reuse the tool list and the discovery result: one hour. */
        const val CACHE_TTL_MILLIS = 3_600_000
    }
}
