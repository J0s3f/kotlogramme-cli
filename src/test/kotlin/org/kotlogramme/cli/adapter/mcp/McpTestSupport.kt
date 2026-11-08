package org.kotlogramme.cli.adapter.mcp

import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.kotlogramme.cli.adapter.mcp.server.McpServer
import org.kotlogramme.cli.adapter.mcp.server.ServerIdentity
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.adapter.mcp.tool.ToolAnnotations
import org.kotlogramme.cli.adapter.mcp.tool.ToolCatalog
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult
import tools.jackson.databind.json.JsonMapper
import kotlin.test.fail

internal const val MODERN = "2026-07-28"
internal const val LEGACY_LATEST = "2025-11-25"
internal const val LEGACY_PREVIOUS = "2025-06-18"

internal val testIdentity = ServerIdentity(name = "kotlogramme", title = "Kotlogramme", version = "9.9.9")

internal const val TEST_INSTRUCTIONS = "Test instructions."

internal fun mcpServer(
    vararg tools: McpTool,
    log: (String) -> Unit = {},
): McpServer = McpServer(testIdentity, TEST_INSTRUCTIONS, ToolCatalog(tools.toList()), log)

internal fun modernMeta(version: String = MODERN, withCapabilities: Boolean = true): JsonObject = buildJsonObject {
    put("io.modelcontextprotocol/protocolVersion", version)
    if (withCapabilities) put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {})
    put(
        "io.modelcontextprotocol/clientInfo",
        buildJsonObject {
            put("name", "test-client")
            put("version", "1.0.0")
        },
    )
}

internal fun requestLine(id: JsonElement?, method: String, params: JsonObject? = null): String =
    buildJsonObject {
        put("jsonrpc", "2.0")
        if (id != null) put("id", id)
        put("method", method)
        if (params != null) put("params", params)
    }.toString()

internal fun modernRequest(
    id: Int,
    method: String,
    params: JsonObject = buildJsonObject {},
    meta: JsonObject = modernMeta(),
): String = requestLine(
    JsonPrimitive(id),
    method,
    JsonObject(params + ("_meta" to meta)),
)

internal fun legacyRequest(id: Int, method: String, params: JsonObject? = null): String =
    requestLine(JsonPrimitive(id), method, params)

internal fun initializeRequest(id: Int = 1, version: String = LEGACY_LATEST): String =
    legacyRequest(
        id,
        "initialize",
        buildJsonObject {
            put("protocolVersion", version)
            put("capabilities", buildJsonObject {})
            put(
                "clientInfo",
                buildJsonObject {
                    put("name", "legacy-client")
                    put("version", "1.0.0")
                },
            )
        },
    )

internal fun callParams(name: String, arguments: JsonObject? = null): JsonObject = buildJsonObject {
    put("name", name)
    if (arguments != null) put("arguments", arguments)
}

internal fun JsonObject.field(name: String): JsonElement = getValue(name)

internal fun parse(line: String?): JsonObject =
    Json.parseToJsonElement(requireNotNull(line) { "the server sent no response" }).jsonObject

internal fun JsonObject.result(): JsonObject = getValue("result").jsonObject

internal fun JsonObject.error(): JsonObject = getValue("error").jsonObject

/** A tool that echoes its `text` argument, which is all the protocol tests need from a tool. */
internal class EchoTool(
    override val name: String = "echo",
    private val behaviour: (JsonObject, ToolContext) -> ToolResult = { arguments, _ ->
        ToolResult.text("echo: ${arguments["text"]?.let { (it as JsonPrimitive).content }.orEmpty()}")
    },
) : McpTool {
    override val title = "Echo"
    override val description = "Repeats the text it is given."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
            },
        )
    }
    override val annotations = ToolAnnotations(readOnly = true)

    override fun call(arguments: JsonObject, context: ToolContext): ToolResult = behaviour(arguments, context)
}

/**
 * The protocol's own JSON Schema, vendored per revision, used as the oracle for what the server sends.
 *
 * A definition is checked by wrapping the whole schema document with a `$ref` to it, so every `$ref`
 * inside the document keeps resolving.
 */
internal object McpSchema {
    private val mapper = JsonMapper()
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
    private val documents = mutableMapOf<String, JsonObject>()

    fun violations(version: String, definition: String, instance: String): List<String> {
        val wrapped = JsonObject(document(version) + ("\$ref" to JsonPrimitive("#/\$defs/$definition")))
        val schema = registry.getSchema(wrapped.toString())
        return schema.validate(mapper.readTree(instance)).map { "${it.instanceLocation}: ${it.message}" }
    }

    fun assertValid(version: String, definition: String, instance: String) {
        val problems = violations(version, definition, instance)
        if (problems.isNotEmpty()) {
            fail("$definition ($version) rejects $instance\n${problems.joinToString("\n")}")
        }
    }

    /** A response line is a valid JSON-RPC response and its `result` is a valid [resultDefinition]. */
    fun assertResultResponse(version: String, resultDefinition: String, line: String?) {
        val text = requireNotNull(line) { "the server sent no response" }
        assertValid(version, "JSONRPCResultResponse", text)
        assertValid(version, resultDefinition, parse(text).result().toString())
    }

    fun assertErrorResponse(version: String, line: String?) {
        assertValid(version, "JSONRPCErrorResponse", requireNotNull(line) { "the server sent no response" })
    }

    private fun document(version: String): JsonObject = documents.getOrPut(version) {
        val resource = "/mcp-schema/$version/schema.json"
        val text = McpSchema::class.java.getResourceAsStream(resource)
            ?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("missing test resource $resource")
        Json.parseToJsonElement(text).jsonObject
    }
}
