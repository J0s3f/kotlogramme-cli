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
        val names = toolNames()

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

    private fun toolNames(vararg options: String): List<String> {
        output.reset()
        cliFixture(mcpCommand = command(modernRequest(1, "tools/list"))).run("mcp", *options)
        return parse(responses().single()).result().getValue("tools").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    @Test
    fun `each access level offers more tools than the one below and keeps all of them`() {
        val readOnly = toolNames()
        val readWrite = toolNames("--access", "read-write")
        val full = toolNames("--access", "full")

        assertEquals(15, readOnly.size)
        assertEquals(26, readWrite.size)
        assertEquals(36, full.size)
        assertTrue(readWrite.containsAll(readOnly) && full.containsAll(readWrite))
        assertTrue("telegram_send_message" in readWrite && "telegram_send_message" !in readOnly)
        assertTrue("telegram_delete_messages" in full && "telegram_delete_messages" !in readWrite)
    }

    @Test
    fun `the file tools appear only when a files directory is given`(@org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        val without = toolNames("--access", "full")
        val with = toolNames("--access", "full", "--files-dir", dir.toString())
        val readOnlyWith = toolNames("--files-dir", dir.toString())

        assertEquals(emptyList(), without.filter { it.startsWith("telegram_send_file") || it == "telegram_download_media" })
        assertEquals(
            listOf("telegram_download_media", "telegram_send_album", "telegram_send_file"),
            (with - without.toSet()).sorted(),
        )
        assertEquals(listOf("telegram_download_media"), (readOnlyWith - toolNames().toSet()))
    }

    @Test
    fun `a files directory that does not exist is a usage error`(@org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        val missing = dir.resolve("nothing")

        val result = cliFixture(mcpCommand = command()).run("mcp", "--files-dir", missing.toString())

        assertTrue(result.statusCode != 0)
        assertTrue(result.stderr.contains("files-dir"), result.stderr)
    }

    @Test
    fun `a writing tool works at read-write and reaches the use case`() {
        val writer = FakeMessageWriter()
        val send = modernRequest(
            2,
            "tools/call",
            buildJsonObject {
                put("name", "telegram_send_message")
                put("arguments", buildJsonObject { put("chat", "@ada"); put("text", "hi") })
            },
        )

        cliFixture(messageWriter = writer, mcpCommand = command(send)).run("mcp", "--access", "read-write")

        McpSchema.assertResultResponse(MODERN, "CallToolResult", responses().single())
        assertEquals(listOf(SendCall("@ada", "hi", null, false)), writer.sends)
    }

    @Test
    fun `a destructive tool is refused at read-write and works at full`() {
        val delete = modernRequest(
            2,
            "tools/call",
            buildJsonObject {
                put("name", "telegram_block_user")
                put("arguments", buildJsonObject { put("user", "@bob") })
            },
        )
        val contacts = FakeContacts()

        cliFixture(contacts = contacts, mcpCommand = command(delete)).run("mcp", "--access", "read-write")
        assertEquals(-32602, parse(responses().single()).getValue("error").jsonObject.getValue("code").jsonPrimitive.int)
        assertEquals(emptyList(), contacts.blocked)

        output.reset()
        cliFixture(contacts = contacts, mcpCommand = command(delete)).run("mcp", "--access", "full")
        McpSchema.assertResultResponse(MODERN, "CallToolResult", responses().single())
        assertEquals(listOf("@bob"), contacts.blocked)
    }

    @Test
    fun `a file tool call stays inside the files directory`(@org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        val sendMedia = FakeSendMedia()
        val escape = modernRequest(
            2,
            "tools/call",
            buildJsonObject {
                put("name", "telegram_send_file")
                put("arguments", buildJsonObject { put("chat", "@ada"); put("file", "../outside.txt") })
            },
        )

        cliFixture(sendMedia = sendMedia, mcpCommand = command(escape))
            .run("mcp", "--access", "read-write", "--files-dir", dir.toString())

        assertEquals(emptyList(), sendMedia.fileSends)
        val result = parse(responses().single()).result()
        assertEquals(true, result.getValue("isError").jsonPrimitive.boolean)
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
    fun `an invalid argument of a tool is a result the model can read and not a protocol error`() {
        val request = modernRequest(
            2,
            "tools/call",
            buildJsonObject {
                put("name", "telegram_read_history")
                put("arguments", buildJsonObject { put("chat", "https://t.me/+AbCdEf") })
            },
        )

        cliFixture(mcpCommand = command(request)).run("mcp")

        McpSchema.assertResultResponse(MODERN, "CallToolResult", responses().single())
        assertEquals(true, parse(responses().single()).result().getValue("isError").jsonPrimitive.boolean)
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
