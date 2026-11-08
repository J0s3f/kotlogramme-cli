package org.kotlogramme.cli.adapter.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.mcp.LEGACY_LATEST
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.files.FilesSandbox
import org.kotlogramme.cli.adapter.mcp.initializeRequest
import org.kotlogramme.cli.adapter.mcp.legacyRequest
import org.kotlogramme.cli.adapter.mcp.mcpServer
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What must hold for every tool the server can ever offer, whichever level it belongs to. */
class ToolCatalogContractTest {
    @TempDir
    lateinit var directory: Path

    private fun everyTool(): List<McpTool> = fakeTelegramTools().all(FilesSandbox.on(directory))

    @Test
    fun `the catalog has the planned number of tools at each level`() {
        val byLevel = everyTool().groupingBy { it.access }.eachCount()

        assertEquals(mapOf(AccessLevel.READ_ONLY to 16, AccessLevel.READ_WRITE to 13, AccessLevel.FULL to 10), byLevel)
    }

    @Test
    fun `names are unique follow the naming rules and carry a real description`() {
        val tools = everyTool()

        assertEquals(tools.size, tools.map { it.name }.toSet().size)
        for (tool in tools) {
            assertTrue(Regex("telegram_[a-z_]+").matches(tool.name), tool.name)
            assertTrue(tool.description.length > 40, tool.name)
            assertTrue(tool.title.isNotBlank(), tool.name)
        }
    }

    @Test
    fun `every argument of every tool is described`() {
        for (tool in everyTool()) {
            val properties = tool.inputSchema["properties"]?.jsonObject.orEmpty()
            for ((argument, schema) in properties) {
                val description = schema.jsonObject["description"]?.jsonPrimitive?.content.orEmpty()
                assertTrue(description.length > 8, "${tool.name}.$argument")
            }
        }
    }

    @Test
    fun `an input schema closes the object and is itself a valid JSON Schema`() {
        for (tool in everyTool()) {
            assertEquals("object", tool.inputSchema.getValue("type").jsonPrimitive.content, tool.name)
            assertEquals(JsonPrimitive(false), tool.inputSchema.getValue("additionalProperties"), tool.name)
            assertEquals(emptyList(), McpSchema.violationsOfSchema(tool.inputSchema.toString()), tool.name)
        }
    }

    @Test
    fun `the annotations agree with the level of every tool`() {
        for (tool in everyTool()) {
            val hints = tool.annotations
            when (tool.access) {
                AccessLevel.READ_ONLY ->
                    assertTrue(!hints.destructive && (hints.readOnly || tool.name == "telegram_download_media"), tool.name)
                AccessLevel.READ_WRITE -> assertTrue(!hints.readOnly && !hints.destructive, tool.name)
                AccessLevel.FULL -> assertTrue(!hints.readOnly && hints.destructive, tool.name)
            }
        }
    }

    @Test
    fun `an unknown argument is rejected by every tool`() {
        for (tool in everyTool()) {
            assertFailsWith<IllegalArgumentException>(tool.name) { tool.callWith("no_such_argument" to "x") }
        }
    }

    @Test
    fun `a tool with required arguments refuses a call that gives none`() {
        for (tool in everyTool().filter { it.inputSchema.containsKey("required") }) {
            assertFailsWith<IllegalArgumentException>(tool.name) { tool.callWith() }
        }
    }

    @Test
    fun `every chat or user argument refuses links so that no tool can join a chat`() {
        // The wait tool only compares its selector with incoming messages and never resolves it.
        for (tool in everyTool().filter { it.name != "telegram_wait_for_message" }) {
            val properties = tool.inputSchema["properties"]?.jsonObject.orEmpty()
            for (argument in properties.keys.filter { it in REFERENCE_ARGUMENTS }) {
                val arguments = validArguments(tool) + (argument to "https://t.me/+AbCdEfGh")

                val problem = assertFailsWith<IllegalArgumentException>("${tool.name}.$argument") {
                    tool.callWith(*arguments.toList().toTypedArray())
                }
                assertTrue(problem.message!!.contains("'$argument'"), "${tool.name}: ${problem.message}")
            }
        }
    }

    /** A value for every argument of [tool], so that a test can spoil exactly one. */
    private fun validArguments(tool: McpTool): Map<String, Any> =
        tool.inputSchema["properties"]?.jsonObject.orEmpty().mapValues { (_, schema) ->
            val definition = schema.jsonObject
            when (definition.getValue("type").jsonPrimitive.content) {
                "integer" -> 1
                "boolean" -> true
                "array" ->
                    if (definition.getValue("items").jsonObject.containsKey("enum")) emptyList<String>()
                    else if (definition.getValue("items").jsonObject.getValue("type").jsonPrimitive.content == "integer") listOf(1)
                    else listOf("a.txt")
                else -> definition["enum"]?.let { (it as JsonArray).first().jsonPrimitive.content } ?: "@sample_name"
            }
        }

    private companion object {
        val REFERENCE_ARGUMENTS = setOf("chat", "from_chat", "to_chat", "user")
    }

    @Test
    fun `the full tool list satisfies the protocol schema in both eras`() {
        val server = mcpServer(*everyTool().toTypedArray())

        McpSchema.assertResultResponse(MODERN, "ListToolsResult", server.handle(modernRequest(1, "tools/list")))
        server.handle(initializeRequest())
        McpSchema.assertResultResponse(LEGACY_LATEST, "ListToolsResult", server.handle(legacyRequest(2, "tools/list")))
    }

    @Test
    fun `the annotations reach the client`() {
        val server = mcpServer(*everyTool().toTypedArray())

        val tools = org.kotlogramme.cli.adapter.mcp.parse(server.handle(modernRequest(1, "tools/list")))
            .getValue("result").jsonObject.getValue("tools") as JsonArray
        val delete = tools.map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == "telegram_delete_messages" }

        assertEquals(JsonPrimitive(true), delete.getValue("annotations").jsonObject.getValue("destructiveHint"))
        assertEquals(JsonPrimitive(false), delete.getValue("annotations").jsonObject.getValue("readOnlyHint"))
    }
}
