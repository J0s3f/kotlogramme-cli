package org.kotlogramme.cli.adapter.mcp.server

import org.kotlogramme.cli.adapter.mcp.EchoTool
import org.kotlogramme.cli.adapter.mcp.mcpServer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whole sessions recorded as text, so a change to any byte the server sends shows up as a diff.
 *
 * Tool order and the cache hints are fixed, which is what makes a byte for byte comparison possible.
 */
class GoldenTranscriptTest {
    private fun transcript(name: String): List<Pair<String, String?>> {
        val text = GoldenTranscriptTest::class.java.getResourceAsStream("/mcp-transcripts/$name.transcript")
            ?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("missing transcript $name")
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        return lines.chunked(2).map { (request, response) ->
            require(request.startsWith("> ") && response.startsWith("< ")) { "bad transcript pair: $request / $response" }
            request.removePrefix("> ") to response.removePrefix("< ").takeIf { it != "-" }
        }
    }

    private fun replay(name: String) {
        val server = mcpServer(EchoTool())
        for ((request, expected) in transcript(name)) {
            assertEquals(expected, server.handle(request), "answer to: $request")
        }
    }

    @Test
    fun `a stateless session is answered exactly as recorded`() = replay("modern")

    @Test
    fun `a handshake session is answered exactly as recorded`() = replay("legacy")
}
