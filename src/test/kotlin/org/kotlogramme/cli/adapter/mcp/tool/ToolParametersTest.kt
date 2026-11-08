package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolParametersTest {
    private val parameters = ToolParameters(
        StringParameter("chat", "The chat.", required = true),
        StringParameter("query", "What to look for."),
        IntParameter("limit", "How many.", min = 1, max = 100, default = 20),
        BooleanParameter("silent", "Without a notification.", default = false),
    )

    private fun parse(vararg pairs: Pair<String, Any>): ToolArguments =
        parameters.parse(
            buildJsonObject {
                pairs.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Int -> put(key, value)
                        is Boolean -> put(key, value)
                    }
                }
            },
        )

    @Test
    fun `the schema describes every parameter and forbids the others`() {
        val schema = parameters.schema

        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        assertEquals(false, schema.getValue("additionalProperties").jsonPrimitive.boolean)
        assertEquals(JsonArray(listOf(JsonPrimitive("chat"))), schema.getValue("required"))
        val properties = schema.getValue("properties").jsonObject
        assertEquals(setOf("chat", "query", "limit", "silent"), properties.keys)
        assertEquals("string", properties.getValue("chat").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("The chat.", properties.getValue("chat").jsonObject.getValue("description").jsonPrimitive.content)
        val limit = properties.getValue("limit").jsonObject
        assertEquals("integer", limit.getValue("type").jsonPrimitive.content)
        assertEquals(1, limit.getValue("minimum").jsonPrimitive.int)
        assertEquals(100, limit.getValue("maximum").jsonPrimitive.int)
        assertEquals(20, limit.getValue("default").jsonPrimitive.int)
        assertEquals("boolean", properties.getValue("silent").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `a tool without parameters uses the schema the specification recommends`() {
        val schema = ToolParameters().schema

        assertEquals(
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
            },
            schema,
        )
    }

    @Test
    fun `values come back typed and defaults fill the gaps`() {
        val arguments = parse("chat" to "@ada")

        assertEquals("@ada", arguments.string("chat"))
        assertNull(arguments.stringOrNull("query"))
        assertEquals(20, arguments.int("limit"))
        assertEquals(false, arguments.boolean("silent"))
    }

    @Test
    fun `given values win over defaults`() {
        val arguments = parse("chat" to "@ada", "query" to "hi", "limit" to 5, "silent" to true)

        assertEquals("hi", arguments.stringOrNull("query"))
        assertEquals(5, arguments.int("limit"))
        assertEquals(true, arguments.boolean("silent"))
    }

    @Test
    fun `a missing required argument is named`() {
        val problem = assertFailsWith<IllegalArgumentException> { parse() }

        assertTrue(problem.message!!.contains("chat"), problem.message)
    }

    @Test
    fun `a blank required string is rejected`() {
        assertFailsWith<IllegalArgumentException> { parse("chat" to "  ") }
    }

    @Test
    fun `an argument nobody declared is rejected and the valid ones are listed`() {
        val problem = assertFailsWith<IllegalArgumentException> { parse("chat" to "@ada", "bogus" to 1) }

        assertTrue(problem.message!!.contains("bogus"), problem.message)
        assertTrue(problem.message!!.contains("limit"), problem.message)
    }

    @Test
    fun `the wrong type is rejected with the expected type`() {
        val notInteger = assertFailsWith<IllegalArgumentException> { parse("chat" to "@ada", "limit" to "ten") }
        val notString = assertFailsWith<IllegalArgumentException> { parse("chat" to 5) }
        val notBoolean = assertFailsWith<IllegalArgumentException> { parse("chat" to "@ada", "silent" to "yes") }

        assertTrue(notInteger.message!!.contains("integer"), notInteger.message)
        assertTrue(notString.message!!.contains("string"), notString.message)
        assertTrue(notBoolean.message!!.contains("boolean"), notBoolean.message)
    }

    @Test
    fun `an integer outside its range is rejected with the range`() {
        for (value in listOf(0, 101, -3)) {
            val problem = assertFailsWith<IllegalArgumentException> { parse("chat" to "@ada", "limit" to value) }

            assertTrue(problem.message!!.contains("between 1 and 100"), problem.message)
        }
    }

    @Test
    fun `a fractional number is not an integer`() {
        val arguments: JsonObject = buildJsonObject {
            put("chat", "@ada")
            put("limit", 2.5)
        }

        assertFailsWith<IllegalArgumentException> { parameters.parse(arguments) }
    }

    @Test
    fun `a string longer than the limit is rejected`() {
        assertFailsWith<IllegalArgumentException> { parse("chat" to "x".repeat(StringParameter.MAX_LENGTH + 1)) }
    }
}
