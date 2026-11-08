package org.kotlogramme.cli.adapter.cli

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.parse
import org.kotlogramme.cli.adapter.mcp.result
import org.kotlogramme.cli.application.port.spi.AppConfig
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
    fun `mcp prints nothing through the regular output`() {
        val fixture = cliFixture(mcpCommand = command(modernRequest(1, "server/discover")))

        fixture.run("mcp")

        assertEquals(emptyList(), fixture.output.lines)
    }
}
