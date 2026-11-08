package org.kotlogramme.cli.adapter.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlogramme.cli.FakeClock
import org.kotlogramme.cli.adapter.cli.FakeListen
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.cli.domain.Message
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaitForMessageToolTest {
    private val clock = FakeClock()
    private val ada = Chat(1, "Ada", ChatKind.PRIVATE, "ada", null, null)
    private val bob = Chat(2, "Bob", ChatKind.PRIVATE, "bob", null, null)

    private fun incoming(chat: Chat, text: String, id: Int = 1, outgoing: Boolean = false) = IncomingUpdate.NewMessage(
        chat,
        Message(id, "Sender", text, Instant.parse("2026-01-01T12:30:00Z"), outgoing),
    )

    private fun args(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Int -> put(key, value)
            }
        }
    }

    private fun wait(listen: Listen, context: ToolContext = ToolContext(), vararg pairs: Pair<String, Any>) =
        WaitForMessageTool({ listen }, clock).call(args(*pairs), context).structured!!.jsonObject

    /** A source that delivers nothing and lets time pass by [step] each time the tool looks at the stop signal. */
    private inner class QuietSource(private val step: Duration) : Listen {
        var looks = 0

        override fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int {
            while (!stop()) {
                looks++
                clock.advance(step)
            }
            return 0
        }
    }

    @Test
    fun `it returns the next incoming message`() {
        val body = wait(FakeListen(listOf(incoming(ada, "hello"))))

        assertEquals(JsonPrimitive(false), body.getValue("timed_out"))
        val message = body.getValue("items").jsonArray.single().jsonObject
        assertEquals("hello", message.getValue("text").jsonPrimitive.content)
        assertEquals("Ada", message.getValue("chat").jsonPrimitive.content)
        assertEquals("1", message.getValue("chat_id").jsonPrimitive.content)
    }

    @Test
    fun `it skips updates that carry no message and messages the account sent itself`() {
        val updates = listOf(
            IncomingUpdate.Other("updateUserStatus"),
            incoming(ada, "my own words", outgoing = true),
            incoming(ada, "a reply", id = 2),
        )

        val body = wait(FakeListen(updates))

        assertEquals("a reply", body.getValue("items").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `it waits for the chosen chat only`() {
        val updates = listOf(incoming(bob, "from bob"), incoming(ada, "from ada", id = 2))

        val body = wait(FakeListen(updates), pairs = arrayOf("chat" to "@ada"))

        assertEquals("from ada", body.getValue("items").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `it knows a chat by id or title as well`() {
        for (selector in listOf("2", "Bob")) {
            val body = wait(FakeListen(listOf(incoming(ada, "x"), incoming(bob, "from bob", id = 2))), pairs = arrayOf("chat" to selector))

            assertEquals("from bob", body.getValue("items").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content, selector)
        }
    }

    @Test
    fun `it gives up after the timeout and says so`() {
        val source = QuietSource(Duration.ofSeconds(5))

        val body = wait(source, pairs = arrayOf("timeout_seconds" to 25))

        assertEquals(JsonPrimitive(true), body.getValue("timed_out"))
        assertEquals(0, body.getValue("items").jsonArray.size)
        assertEquals(5, source.looks)
    }

    @Test
    fun `it waits thirty seconds by default`() {
        val source = QuietSource(Duration.ofSeconds(5))

        wait(source)

        assertEquals(6, source.looks)
    }

    @Test
    fun `it stops as soon as the client cancels the request`() {
        val source = QuietSource(Duration.ofSeconds(1))
        var checks = 0

        val body = wait(source, ToolContext(isCancelled = { ++checks > 3 }), "timeout_seconds" to 300)

        assertEquals(JsonPrimitive(true), body.getValue("timed_out"))
        assertTrue(source.looks < 10, "kept waiting after the cancellation: ${source.looks}")
    }

    @Test
    fun `the timeout must be between one and three hundred seconds`() {
        for (seconds in listOf(0, 301)) {
            assertFailsWith<IllegalArgumentException> { wait(FakeListen(), pairs = arrayOf("timeout_seconds" to seconds)) }
        }
    }

    @Test
    fun `a chat that is not a reference is rejected before listening`() {
        assertFailsWith<IllegalArgumentException> { wait(FakeListen(), pairs = arrayOf("chat" to "x".repeat(5000))) }
    }
}
