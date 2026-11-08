package org.kotlogramme.cli.adapter.cli

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlogramme.TelegramException
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.parse
import org.kotlogramme.cli.adapter.mcp.result
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.domain.Chat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpCommandTest {
    private val output = ByteArrayOutputStream()

    private fun command(vararg requests: String) = McpCommand(
        input = ByteArrayInputStream((requests.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)),
        claimOutput = { output },
    )

    private fun responses(): List<String> = String(output.toByteArray(), Charsets.UTF_8).lines().filter { it.isNotEmpty() }

    @Test
    fun `mcp is a command of the client`() {
        val help = cliFixture().run("--help").stdout

        assertTrue(help.contains("mcp"), help)
    }

    @Test
    fun `mcp answers on the stream it was given and ends with the input`() {
        val fixture = cliFixture(mcpCommand = command(modernRequest(1, "server/discover")))

        val result = fixture.run("mcp")

        assertEquals(0, result.statusCode, result.stderr)
        McpSchema.assertResultResponse(MODERN, "DiscoverResult", responses().single())
        val meta = parse(responses().single()).result().getValue("_meta").jsonObject
        val info = meta.getValue("io.modelcontextprotocol/serverInfo").jsonObject
        assertEquals("kotlogramme", info.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `mcp needs no Telegram credentials just to answer the protocol`() {
        val noCredentials = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite"))
        val fixture = cliFixture(config = noCredentials, mcpCommand = command(modernRequest(1, "tools/list")))

        val result = fixture.run("mcp")

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals(1, responses().size)
    }

    @Test
    fun `mcp offers the reading tools by default`() {
        val names = cliFixture(mcpCommand = command(modernRequest(1, "tools/list"))).let {
            it.run("mcp")
            parse(responses().single()).result().getValue("tools").jsonArray
                .map { tool -> tool.jsonObject.getValue("name").jsonPrimitive.content }
        }

        assertEquals(names.sorted(), names)
        assertTrue(
            names.containsAll(listOf("telegram_whoami", "telegram_list_chats", "telegram_read_history", "telegram_wait_for_message")),
            names.toString(),
        )
        assertTrue(names.none { it.startsWith("telegram_send") || it.startsWith("telegram_delete") }, names.toString())
    }

    @Test
    fun `mcp accepts the access levels and nothing else`() {
        for (level in listOf("read-only", "read-write", "full")) {
            output.reset()
            val fixture = cliFixture(mcpCommand = command(modernRequest(1, "tools/list")))

            assertEquals(0, fixture.run("mcp", "--access", level).statusCode, level)
        }
        val rejected = cliFixture(mcpCommand = command()).run("mcp", "--access", "root")

        assertTrue(rejected.statusCode != 0)
        assertTrue(rejected.stderr.contains("access"), rejected.stderr)
    }

    @Test
    fun `a tool call reaches the use case and returns its data`() {
        val request = modernRequest(
            2,
            "tools/call",
            buildJsonObject { put("name", "telegram_whoami") },
        )
        val fixture = cliFixture(
            authenticate = FakeAuthenticate(AccountStatus.SignedIn(testAccount)),
            mcpCommand = command(request),
        )

        fixture.run("mcp")

        McpSchema.assertResultResponse(MODERN, "CallToolResult", responses().single())
        val body = parse(responses().single()).result().getValue("structuredContent").jsonObject
        assertEquals("42", body.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `missing credentials come back as a tool error the model can read`() {
        val noCredentials = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite"))
        val request = modernRequest(2, "tools/call", buildJsonObject { put("name", "telegram_list_chats") })

        cliFixture(config = noCredentials, mcpCommand = command(request)).run("mcp")

        val result = parse(responses().single()).result()
        assertEquals(true, result.getValue("isError").jsonPrimitive.boolean)
        assertTrue(result.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content.contains("credentials"))
    }

    @Test
    fun `a rejected Telegram request comes back as a tool error and not as a protocol error`() {
        val failing = object : ListDialogs {
            override fun list(limit: Int, cursor: String?, all: Boolean): List<Chat> =
                throw TelegramException("FLOOD_WAIT_30")
        }
        val request = modernRequest(2, "tools/call", buildJsonObject { put("name", "telegram_list_chats") })

        cliFixture(listDialogs = failing, mcpCommand = command(request)).run("mcp")

        McpSchema.assertResultResponse(MODERN, "CallToolResult", responses().single())
        val result = parse(responses().single()).result()
        assertEquals(true, result.getValue("isError").jsonPrimitive.boolean)
        assertTrue(result.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content.contains("FLOOD_WAIT_30"))
    }

    @Test
    fun `a tool above the chosen access level is refused even when called by name`() {
        val request = modernRequest(2, "tools/call", buildJsonObject { put("name", "telegram_send_message") })

        cliFixture(mcpCommand = command(request)).run("mcp")

        assertEquals(-32602, parse(responses().single()).getValue("error").jsonObject.getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `mcp prints nothing through the regular output`() {
        val fixture = cliFixture(mcpCommand = command(modernRequest(1, "server/discover")))

        fixture.run("mcp")

        assertEquals(emptyList(), fixture.output.lines)
    }
}
