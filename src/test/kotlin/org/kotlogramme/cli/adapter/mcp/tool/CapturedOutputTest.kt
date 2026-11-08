package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a command would print becomes one JSON object a tool can return. */
class CapturedOutputTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `a table becomes the items array`() {
        val output = CapturedOutput()

        output.table(listOf("id", "name"), listOf(listOf("1", "Ada"), listOf("2", "Bob")))

        assertEquals(
            json("""{"items":[{"id":"1","name":"Ada"},{"id":"2","name":"Bob"}]}"""),
            output.result(),
        )
    }

    @Test
    fun `an empty table is an empty items array and not a missing one`() {
        val output = CapturedOutput()

        output.table(listOf("id"), emptyList())

        assertEquals(json("""{"items":[]}"""), output.result())
    }

    @Test
    fun `a short row is padded with empty cells`() {
        val output = CapturedOutput()

        output.table(listOf("a", "b"), listOf(listOf("x")))

        assertEquals(json("""{"items":[{"a":"x","b":""}]}"""), output.result())
    }

    @Test
    fun `the next page cursor sits beside the items`() {
        val output = CapturedOutput()

        output.table(listOf("id"), listOf(listOf("1")))
        output.nextPage("42")

        assertEquals(json("""{"items":[{"id":"1"}],"next_cursor":"42"}"""), output.result())
    }

    @Test
    fun `a message count record and details become fields`() {
        val output = CapturedOutput()

        output.message("Done.")
        output.count(7)
        output.record(listOf("path" to "a.png", "size" to "12"))
        output.details("Signed in as Ada", listOf("id" to "42"))

        assertEquals(
            json("""{"message":"Signed in as Ada","count":7,"path":"a.png","size":"12","id":"42"}"""),
            output.result(),
        )
    }

    @Test
    fun `properties get snake case keys`() {
        val output = CapturedOutput()

        output.properties(listOf("Session path" to "s.sqlite"))

        assertEquals(json("""{"session_path":"s.sqlite"}"""), output.result())
    }

    @Test
    fun `nothing printed is an empty object`() {
        assertEquals(JsonObject(emptyMap()), CapturedOutput().result())
    }

    @Test
    fun `plain lines are kept as a message so no text is lost`() {
        val output = CapturedOutput()

        output.line("something")

        assertEquals(json("""{"message":"something"}"""), output.result())
    }
}
