package org.kotlogramme.cli.adapter.mcp.transport

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlogramme.cli.adapter.mcp.EchoTool
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
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StdioTransportTest {
    private fun serve(
        input: ByteArray,
        server: org.kotlogramme.cli.adapter.mcp.server.McpServer = mcpServer(EchoTool()),
        maxLineBytes: Int = StdioTransport.DEFAULT_MAX_LINE_BYTES,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        StdioTransport(ByteArrayInputStream(input), output, server, maxLineBytes).run()
        return output.toByteArray()
    }

    private fun lines(bytes: ByteArray): List<String> =
        String(bytes, Charsets.UTF_8).split("\n").dropLast(1)

    private fun text(vararg messages: String): ByteArray = (messages.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)

    @Test
    fun `each response is one line ended by a line feed`() {
        val output = serve(text(modernRequest(1, "tools/list"), modernRequest(2, "tools/list")))

        assertTrue(String(output, Charsets.UTF_8).endsWith("}\n"))
        assertFalse(String(output, Charsets.UTF_8).contains('\r'))
        assertEquals(listOf(1, 2), lines(output).map { parse(it).getValue("id").jsonPrimitive.int })
    }

    @Test
    fun `responses come back in request order`() {
        val requests = (1..20).map { modernRequest(it, "tools/list") }

        val ids = lines(serve(text(*requests.toTypedArray()))).map { parse(it).getValue("id").jsonPrimitive.int }

        assertEquals((1..20).toList(), ids)
    }

    @Test
    fun `notifications and blank lines produce no output`() {
        val output = serve(text("", requestLine(null, "notifications/initialized"), "  ", legacyRequest(1, "ping")))

        assertEquals(1, lines(output).size)
        assertEquals(1, parse(lines(output).single()).getValue("id").jsonPrimitive.int)
    }

    @Test
    fun `text with newlines and non ASCII characters stays on one line and survives`() {
        val message = "line one\nline two ä中😀"
        val request = modernRequest(1, "tools/call", callParams("echo", buildJsonObject { put("text", message) }))

        val output = serve(text(request))

        val response = lines(output).single()
        val shown = parse(response).result().getValue("content").jsonArray.single().jsonObject
            .getValue("text").jsonPrimitive.content
        assertEquals("echo: $message", shown)
    }

    @Test
    fun `invalid JSON is answered and the stream carries on`() {
        val output = serve(text("{broken", legacyRequest(7, "ping")))

        val responses = lines(output).map(::parse)
        assertEquals(McpErrorCodes.PARSE_ERROR, responses[0].error().getValue("code").jsonPrimitive.int)
        assertNull(responses[0]["id"])
        assertEquals(JsonPrimitive(7), responses[1].getValue("id"))
    }

    @Test
    fun `a line over the limit is rejected without stopping the stream`() {
        val huge = requestLine(JsonPrimitive(1), "tools/call", buildJsonObject { put("name", "x".repeat(2000)) })

        val output = serve(text(huge, legacyRequest(2, "ping")), maxLineBytes = 500)

        val responses = lines(output).map(::parse)
        assertEquals(McpErrorCodes.INVALID_REQUEST, responses[0].error().getValue("code").jsonPrimitive.int)
        assertNull(responses[0]["id"])
        assertEquals(JsonPrimitive(2), responses[1].getValue("id"))
    }

    @Test
    fun `a megabyte sized message is handled`() {
        val big = "a".repeat(1_000_000)
        val request = modernRequest(1, "tools/call", callParams("echo", buildJsonObject { put("text", big) }))

        val output = serve(text(request))

        val shown = parse(lines(output).single()).result().getValue("content").jsonArray.single().jsonObject
            .getValue("text").jsonPrimitive.content
        assertEquals(big.length + "echo: ".length, shown.length)
    }

    @Test
    fun `the end of input ends the run and flushes pending responses`() {
        val output = serve(text(initializeRequest(1)))

        assertEquals(1, lines(output).size)
    }

    @Test
    fun `a last request without a line feed is still answered`() {
        val output = serve(legacyRequest(1, "ping").toByteArray(Charsets.UTF_8))

        assertEquals(1, lines(output).size)
    }

    @Test
    fun `a cancelled request gets no response and later requests are still served`() {
        val started = CountDownLatch(1)
        val waiting = EchoTool("wait") { _, context ->
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!context.isCancelled() && System.nanoTime() < deadline) Thread.sleep(5)
            ToolResult.text(if (context.isCancelled()) "cancelled" else "timed out")
        }
        val toServer = PipedOutputStream()
        val input = PipedInputStream(toServer, 1 shl 16)
        val output = ByteArrayOutputStream()
        val transport = Thread { StdioTransport(input, output, mcpServer(waiting)).run() }
        transport.start()

        toServer.write(text(modernRequest(1, "tools/call", callParams("wait"))))
        toServer.flush()
        assertTrue(started.await(5, TimeUnit.SECONDS), "the tool never started")
        toServer.write(
            text(
                requestLine(null, "notifications/cancelled", buildJsonObject { put("requestId", 1) }),
                legacyRequest(2, "ping"),
            ),
        )
        toServer.close()
        transport.join(10_000)

        assertFalse(transport.isAlive, "the transport did not stop at the end of input")
        val responses = lines(output.toByteArray()).map(::parse)
        assertEquals(listOf(JsonPrimitive(2)), responses.map { it.getValue("id") })
    }
}
