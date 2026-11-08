package org.kotlogramme.cli.adapter.mcp.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlogramme.cli.adapter.mcp.EchoTool
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.callParams
import org.kotlogramme.cli.adapter.mcp.error
import org.kotlogramme.cli.adapter.mcp.mcpServer
import org.kotlogramme.cli.adapter.mcp.modernMeta
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.parse
import org.kotlogramme.cli.adapter.mcp.protocol.McpErrorCodes
import org.kotlogramme.cli.adapter.mcp.requestLine
import org.kotlogramme.cli.adapter.mcp.result
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The stateless revision: every request carries its own version and capabilities. */
class McpServerModernTest {
    private val serverInfoKey = "io.modelcontextprotocol/serverInfo"

    @Test
    fun `server discover reports versions capabilities and identity`() {
        val line = mcpServer().handle(modernRequest(1, "server/discover"))

        McpSchema.assertResultResponse(MODERN, "DiscoverResult", line)
        val result = parse(line).result()
        assertEquals("complete", result.getValue("resultType").jsonPrimitive.content)
        assertEquals(
            listOf("2026-07-28", "2025-11-25", "2025-06-18"),
            result.getValue("supportedVersions").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(false, result.getValue("capabilities").jsonObject.getValue("tools").jsonObject
            .getValue("listChanged").jsonPrimitive.boolean)
        assertTrue(result.getValue("instructions").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun `every result names the server in its metadata`() {
        val result = parse(mcpServer().handle(modernRequest(1, "server/discover"))).result()

        val info = result.getValue("_meta").jsonObject.getValue(serverInfoKey).jsonObject
        assertEquals("kotlogramme", info.getValue("name").jsonPrimitive.content)
        assertEquals("9.9.9", info.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `the request id comes back unchanged whether it is a string or an integer`() {
        val server = mcpServer()

        val integer = parse(server.handle(requestLine(JsonPrimitive(42), "server/discover", paramsWithMeta())))
        val string = parse(server.handle(requestLine(JsonPrimitive("abc-1"), "server/discover", paramsWithMeta())))

        assertEquals(JsonPrimitive(42), integer.getValue("id"))
        assertEquals(JsonPrimitive("abc-1"), string.getValue("id"))
    }

    @Test
    fun `a request without metadata is invalid params`() {
        val line = mcpServer().handle(requestLine(JsonPrimitive(5), "tools/list", buildJsonObject {}))

        McpSchema.assertErrorResponse(MODERN, line)
        val response = parse(line)
        assertEquals(McpErrorCodes.INVALID_PARAMS, response.error().getValue("code").jsonPrimitive.int)
        assertEquals(JsonPrimitive(5), response.getValue("id"))
    }

    @Test
    fun `a request without params at all is invalid params`() {
        val line = mcpServer().handle(requestLine(JsonPrimitive(5), "tools/list"))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `a request without client capabilities is invalid params`() {
        val line = mcpServer().handle(
            modernRequest(1, "tools/list", meta = modernMeta(withCapabilities = false)),
        )

        McpSchema.assertErrorResponse(MODERN, line)
        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `an unsupported version lists the supported ones and echoes the requested one`() {
        val line = mcpServer().handle(modernRequest(1, "tools/list", meta = modernMeta(version = "1900-01-01")))

        McpSchema.assertValid(MODERN, "UnsupportedProtocolVersionError", requireNotNull(line))
        val error = parse(line).error()
        assertEquals(McpErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, error.getValue("code").jsonPrimitive.int)
        val data = error.getValue("data").jsonObject
        assertEquals("1900-01-01", data.getValue("requested").jsonPrimitive.content)
        assertEquals(
            listOf("2026-07-28", "2025-11-25", "2025-06-18"),
            data.getValue("supported").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `a metadata version that is not a string is invalid params`() {
        val meta = JsonObject(modernMeta() + ("io.modelcontextprotocol/protocolVersion" to JsonPrimitive(7)))

        val line = mcpServer().handle(modernRequest(1, "tools/list", meta = meta))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `tools list is empty without tools and carries the cache hints`() {
        val line = mcpServer().handle(modernRequest(1, "tools/list"))

        McpSchema.assertResultResponse(MODERN, "ListToolsResult", line)
        val result = parse(line).result()
        assertEquals(JsonArray(emptyList()), result.getValue("tools"))
        assertEquals("complete", result.getValue("resultType").jsonPrimitive.content)
        assertEquals("public", result.getValue("cacheScope").jsonPrimitive.content)
        assertTrue(result.getValue("ttlMs").jsonPrimitive.int > 0)
        assertNull(result["nextCursor"])
    }

    @Test
    fun `tools list is sorted by name and describes each tool completely`() {
        val server = mcpServer(EchoTool("zulu"), EchoTool("alpha"))

        val line = server.handle(modernRequest(1, "tools/list"))

        McpSchema.assertResultResponse(MODERN, "ListToolsResult", line)
        val tools = parse(line).result().getValue("tools").jsonArray.map { it.jsonObject }
        assertEquals(listOf("alpha", "zulu"), tools.map { it.getValue("name").jsonPrimitive.content })
        val first = tools.first()
        assertEquals("Echo", first.getValue("title").jsonPrimitive.content)
        assertTrue(first.getValue("description").jsonPrimitive.content.isNotBlank())
        assertEquals("object", first.getValue("inputSchema").jsonObject.getValue("type").jsonPrimitive.content)
        val hints = first.getValue("annotations").jsonObject
        assertEquals(true, hints.getValue("readOnlyHint").jsonPrimitive.boolean)
        assertEquals(false, hints.getValue("destructiveHint").jsonPrimitive.boolean)
        assertEquals(true, hints.getValue("idempotentHint").jsonPrimitive.boolean)
        assertEquals(true, hints.getValue("openWorldHint").jsonPrimitive.boolean)
    }

    @Test
    fun `tools call returns text and structured content`() {
        val tool = EchoTool { arguments, _ ->
            ToolResult.json("""{"echo":"${arguments.getValue("text").jsonPrimitive.content}"}""")
        }

        val line = mcpServer(tool).handle(
            modernRequest(2, "tools/call", callParams("echo", buildJsonObject { put("text", "hi") })),
        )

        McpSchema.assertResultResponse(MODERN, "CallToolResult", line)
        val result = parse(line).result()
        assertEquals("complete", result.getValue("resultType").jsonPrimitive.content)
        assertEquals(false, result.getValue("isError").jsonPrimitive.boolean)
        val content = result.getValue("content").jsonArray.single().jsonObject
        assertEquals("text", content.getValue("type").jsonPrimitive.content)
        assertEquals("""{"echo":"hi"}""", content.getValue("text").jsonPrimitive.content)
        assertEquals("hi", result.getValue("structuredContent").jsonObject.getValue("echo").jsonPrimitive.content)
    }

    @Test
    fun `a call without arguments passes an empty object`() {
        var received: JsonObject? = null
        val tool = EchoTool { arguments, _ ->
            received = arguments
            ToolResult.text("ok")
        }

        mcpServer(tool).handle(modernRequest(2, "tools/call", callParams("echo")))

        assertEquals(JsonObject(emptyMap()), received)
    }

    @Test
    fun `a tool that rejects its input reports a tool error the model can read`() {
        val tool = EchoTool { _, _ -> throw IllegalArgumentException("limit must be positive") }

        val line = mcpServer(tool).handle(modernRequest(2, "tools/call", callParams("echo")))

        McpSchema.assertResultResponse(MODERN, "CallToolResult", line)
        val result = parse(line).result()
        assertEquals(true, result.getValue("isError").jsonPrimitive.boolean)
        assertEquals(
            "limit must be positive",
            result.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content,
        )
    }

    @Test
    fun `a tool that returns an error result is a tool error`() {
        val tool = EchoTool { _, _ -> ToolResult.error("Not signed in") }

        val result = parse(mcpServer(tool).handle(modernRequest(2, "tools/call", callParams("echo")))).result()

        assertEquals(true, result.getValue("isError").jsonPrimitive.boolean)
    }

    @Test
    fun `an unexpected failure is an internal error that is logged and does not leak details`() {
        val logged = mutableListOf<String>()
        val tool = EchoTool { _, _ -> throw IllegalStateException("secret internals") }

        val line = mcpServer(tool, log = logged::add).handle(modernRequest(2, "tools/call", callParams("echo")))

        McpSchema.assertErrorResponse(MODERN, line)
        val error = parse(line).error()
        assertEquals(McpErrorCodes.INTERNAL_ERROR, error.getValue("code").jsonPrimitive.int)
        assertTrue(!error.getValue("message").jsonPrimitive.content.contains("secret"))
        assertTrue(logged.any { it.contains("secret internals") })
    }

    @Test
    fun `an unknown tool is invalid params`() {
        val line = mcpServer().handle(modernRequest(2, "tools/call", callParams("nope")))

        McpSchema.assertErrorResponse(MODERN, line)
        val error = parse(line).error()
        assertEquals(McpErrorCodes.INVALID_PARAMS, error.getValue("code").jsonPrimitive.int)
        assertTrue(error.getValue("message").jsonPrimitive.content.contains("nope"))
    }

    @Test
    fun `a call without a tool name is invalid params`() {
        val line = mcpServer().handle(modernRequest(2, "tools/call", buildJsonObject {}))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `arguments that are not an object are invalid params`() {
        val params = buildJsonObject {
            put("name", "echo")
            put("arguments", "text")
        }

        val line = mcpServer(EchoTool()).handle(modernRequest(2, "tools/call", params))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `methods the stateless revision removed are not found`() {
        val server = mcpServer()

        for (method in listOf("ping", "logging/setLevel", "resources/list", "prompts/list", "nope/nothing")) {
            val line = server.handle(modernRequest(3, method))

            McpSchema.assertErrorResponse(MODERN, line)
            assertEquals(
                McpErrorCodes.METHOD_NOT_FOUND,
                parse(line).error().getValue("code").jsonPrimitive.int,
                method,
            )
        }
    }

    @Test
    fun `an error response keeps the id of its request`() {
        val line = mcpServer().handle(requestLine(JsonPrimitive("req-9"), "nope", buildJsonObject {}))

        assertEquals(JsonPrimitive("req-9"), parse(line).getValue("id"))
    }

    @Test
    fun `notifications are never answered`() {
        val server = mcpServer()

        for (method in listOf("notifications/initialized", "notifications/cancelled", "notifications/whatever")) {
            assertNull(server.handle(requestLine(null, method, buildJsonObject {})), method)
        }
    }

    @Test
    fun `malformed input is answered with an error that has no id`() {
        val line = mcpServer().handle("{broken")

        McpSchema.assertErrorResponse(MODERN, line)
        val response = parse(line)
        assertEquals(McpErrorCodes.PARSE_ERROR, response.error().getValue("code").jsonPrimitive.int)
        assertNull(response["id"])
    }

    private fun paramsWithMeta(): JsonObject = buildJsonObject { put("_meta", modernMeta()) }
}
