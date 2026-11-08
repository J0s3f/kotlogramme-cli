package org.kotlogramme.cli.adapter.mcp.transport

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcError
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcMessage
import org.kotlogramme.cli.adapter.mcp.protocol.JsonRpcResponses
import org.kotlogramme.cli.adapter.mcp.protocol.McpErrorCodes
import org.kotlogramme.cli.adapter.mcp.server.CancellationSignal
import org.kotlogramme.cli.adapter.mcp.server.McpServer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The stdio binding of MCP: one JSON-RPC message per line in, one per line out, until the input ends.
 * Specification: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio
 *
 * This is the only class that uses threads. The calling thread reads, because a `notifications/cancelled`
 * has to be seen while a call is still running; one worker thread executes the messages in the order they
 * arrived, because the Telegram client is not thread safe and responses must not overtake each other.
 * A cancelled request gets no response, which is what the specification asks for.
 *
 * Nothing but protocol messages is ever written to [output].
 */
class StdioTransport(
    input: InputStream,
    private val output: OutputStream,
    private val server: McpServer,
    maxLineBytes: Int = DEFAULT_MAX_LINE_BYTES,
) {
    private val lines = BoundedLineReader(input, maxLineBytes)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "mcp-worker").apply { isDaemon = true }
    }
    private val inFlight: MutableSet<JsonElement> = ConcurrentHashMap.newKeySet()
    private val cancelled: MutableSet<JsonElement> = ConcurrentHashMap.newKeySet()
    private val signal = CancellationSignal { it in cancelled }

    @Volatile
    private var outputBroken = false

    /** Serves until the input ends or the output breaks, then lets queued work finish. */
    fun run() {
        try {
            while (!outputBroken) {
                when (val line = lines.next() ?: break) {
                    is Line.TooLong -> worker.execute { send(tooLong()) }
                    is Line.Text -> if (line.value.isNotBlank()) accept(JsonRpcMessage.parse(line.value))
                }
            }
        } finally {
            worker.shutdown()
            if (!worker.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) worker.shutdownNow()
        }
    }

    private fun accept(message: JsonRpcMessage) {
        if (message is JsonRpcMessage.Notification && message.method == CANCELLED) {
            cancel(message)
            return
        }
        if (message is JsonRpcMessage.Request) inFlight += message.id
        worker.execute { serve(message) }
    }

    private fun cancel(notification: JsonRpcMessage.Notification) {
        val id = notification.params?.get("requestId") ?: return
        if (id in inFlight) cancelled += id
    }

    private fun serve(message: JsonRpcMessage) {
        val id = (message as? JsonRpcMessage.Request)?.id
        try {
            val response = server.handle(message, signal)
            if (response != null && (id == null || id !in cancelled)) send(response)
        } finally {
            if (id != null) {
                inFlight -= id
                cancelled -= id
            }
        }
    }

    private fun send(response: JsonObject) {
        try {
            output.write((response.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (broken: IOException) {
            outputBroken = true
        }
    }

    private fun tooLong() = JsonRpcResponses.error(
        null,
        JsonRpcError(McpErrorCodes.INVALID_REQUEST, "The message is too large"),
    )

    companion object {
        /** The longest message accepted: 4 MiB, far above any tool call and far below the heap. */
        const val DEFAULT_MAX_LINE_BYTES = 4 * 1024 * 1024

        private const val SHUTDOWN_GRACE_SECONDS = 5L
        private const val CANCELLED = "notifications/cancelled"
    }
}
