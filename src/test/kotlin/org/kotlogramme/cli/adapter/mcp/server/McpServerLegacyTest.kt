package org.kotlogramme.cli.adapter.mcp.server

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
import org.kotlogramme.cli.adapter.mcp.LEGACY_LATEST
import org.kotlogramme.cli.adapter.mcp.LEGACY_PREVIOUS
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.callParams
import org.kotlogramme.cli.adapter.mcp.error
import org.kotlogramme.cli.adapter.mcp.initializeRequest
import org.kotlogramme.cli.adapter.mcp.legacyRequest
import org.kotlogramme.cli.adapter.mcp.mcpServer
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.parse
import org.kotlogramme.cli.adapter.mcp.protocol.McpErrorCodes
import org.kotlogramme.cli.adapter.mcp.requestLine
import org.kotlogramme.cli.adapter.mcp.result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Clients that predate the stateless revision open with `initialize`; the same process serves them. */
class McpServerLegacyTest {
    @Test
    fun `initialize answers with the requested version when it is supported`() {
        for (version in listOf(LEGACY_LATEST, LEGACY_PREVIOUS)) {
            val line = mcpServer().handle(initializeRequest(version = version))

            McpSchema.assertResultResponse(LEGACY_LATEST, "InitializeResult", line)
            assertEquals(version, parse(line).result().getValue("protocolVersion").jsonPrimitive.content, version)
        }
    }

    @Test
    fun `initialize answers with the latest legacy version when the requested one is unknown`() {
        for (version in listOf("1900-01-01", "2024-11-05", MODERN)) {
            val line = mcpServer().handle(initializeRequest(version = version))

            assertEquals(LEGACY_LATEST, parse(line).result().getValue("protocolVersion").jsonPrimitive.content, version)
        }
    }

    @Test
    fun `initialize describes the server and its instructions`() {
        val result = parse(mcpServer().handle(initializeRequest())).result()

        val info = result.getValue("serverInfo").jsonObject
        assertEquals("kotlogramme", info.getValue("name").jsonPrimitive.content)
        assertEquals("9.9.9", info.getValue("version").jsonPrimitive.content)
        assertEquals(
            false,
            result.getValue("capabilities").jsonObject.getValue("tools").jsonObject
                .getValue("listChanged").jsonPrimitive.boolean,
        )
        assertTrue(result.getValue("instructions").jsonPrimitive.content.isNotBlank())
        assertNull(result["resultType"], "legacy results have no resultType")
    }

    @Test
    fun `initialize without a protocol version is invalid params`() {
        val line = mcpServer().handle(legacyRequest(1, "initialize", buildJsonObject {}))

        McpSchema.assertErrorResponse(LEGACY_LATEST, line)
        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `after initialize requests need no metadata and carry no result type`() {
        val server = mcpServer(EchoTool())
        server.handle(initializeRequest())

        val list = server.handle(legacyRequest(2, "tools/list"))

        McpSchema.assertResultResponse(LEGACY_LATEST, "ListToolsResult", list)
        val result = parse(list).result()
        assertEquals("echo", result.getValue("tools").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertNull(result["resultType"])
        assertNull(result["ttlMs"])
    }

    @Test
    fun `after initialize a tool call returns a legacy result`() {
        val server = mcpServer(EchoTool())
        server.handle(initializeRequest())

        val line = server.handle(
            legacyRequest(3, "tools/call", callParams("echo", buildJsonObject { put("text", "hi") })),
        )

        McpSchema.assertResultResponse(LEGACY_LATEST, "CallToolResult", line)
        val result = parse(line).result()
        assertNull(result["resultType"])
        assertEquals(
            "echo: hi",
            result.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content,
        )
    }

    @Test
    fun `the initialized notification is accepted silently`() {
        val server = mcpServer()
        server.handle(initializeRequest())

        assertNull(server.handle(requestLine(null, "notifications/initialized")))
    }

    @Test
    fun `ping is answered with an empty result`() {
        val server = mcpServer()

        val before = server.handle(legacyRequest(1, "ping"))
        server.handle(initializeRequest(2))
        val after = server.handle(legacyRequest(3, "ping"))

        for (line in listOf(before, after)) {
            McpSchema.assertResultResponse(LEGACY_LATEST, "EmptyResult", line)
            assertEquals(JsonObject(emptyMap()), parse(line).result())
        }
    }

    @Test
    fun `before initialize a request without metadata is invalid params`() {
        val line = mcpServer().handle(legacyRequest(1, "tools/list"))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `a request with modern metadata is still served statelessly after initialize`() {
        val server = mcpServer()
        server.handle(initializeRequest())

        val line = server.handle(modernRequest(2, "tools/list"))

        assertEquals("complete", parse(line).result().getValue("resultType").jsonPrimitive.content)
    }

    @Test
    fun `discover needs metadata even in a legacy session`() {
        val server = mcpServer()
        server.handle(initializeRequest())

        val line = server.handle(legacyRequest(2, "server/discover"))

        assertEquals(McpErrorCodes.INVALID_PARAMS, parse(line).error().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `an unknown method is not found in a legacy session`() {
        val server = mcpServer()
        server.handle(initializeRequest())

        val line = server.handle(legacyRequest(2, "resources/list"))

        McpSchema.assertErrorResponse(LEGACY_LATEST, line)
        assertEquals(McpErrorCodes.METHOD_NOT_FOUND, parse(line).error().getValue("code").jsonPrimitive.int)
        assertEquals(JsonPrimitive(2), parse(line).getValue("id"))
    }
}
