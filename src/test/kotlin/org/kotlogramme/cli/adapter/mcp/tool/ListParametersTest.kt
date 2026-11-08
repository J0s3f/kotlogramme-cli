package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kotlogramme.cli.adapter.mcp.McpSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListParametersTest {
    private fun ints(vararg values: Int): JsonObject = buildJsonObject {
        put("ids", JsonArray(values.map(::JsonPrimitive)))
    }

    private val ids = ToolParameters(IntListParameter("ids", "Some ids.", min = 1, max = 1000, maxItems = 3))

    private val words = ToolParameters(ChoiceListParameter("rights", "Some rights.", listOf("read", "write")))

    private val one = ToolParameters(ChoiceParameter("mode", "A mode.", listOf("fast", "slow"), default = "fast"))

    @Test
    fun `an integer list is read back in order`() {
        assertEquals(listOf(5, 2, 9), ids.parse(ints(5, 2, 9)).ints("ids"))
    }

    @Test
    fun `an integer list refuses empty, too long, repeated and out of range entries`() {
        assertFailsWith<IllegalArgumentException> { ids.parse(ints()) }
        assertFailsWith<IllegalArgumentException> { ids.parse(ints(1, 2, 3, 4)) }
        assertFailsWith<IllegalArgumentException> { ids.parse(ints(2, 2)) }
        assertFailsWith<IllegalArgumentException> { ids.parse(ints(0)) }
        assertFailsWith<IllegalArgumentException> { ids.parse(ints(1001)) }
    }

    @Test
    fun `an integer list refuses text where numbers belong`() {
        val text = buildJsonObject { put("ids", JsonArray(listOf(JsonPrimitive("1")))) }
        val scalar = buildJsonObject { put("ids", 1) }
        val fractional = buildJsonObject { put("ids", JsonArray(listOf(JsonPrimitive(1.5)))) }

        listOf(text, scalar, fractional).forEach { assertFailsWith<IllegalArgumentException> { ids.parse(it) } }
    }

    @Test
    fun `a missing required list is reported by name`() {
        val problem = assertFailsWith<IllegalArgumentException> { ids.parse(buildJsonObject {}) }

        assertTrue(problem.message!!.contains("ids"), problem.message)
    }

    @Test
    fun `a choice list may be empty and defaults to empty`() {
        assertEquals(emptyList(), words.parse(buildJsonObject {}).strings("rights"))
        assertEquals(
            emptyList(),
            words.parse(buildJsonObject { put("rights", JsonArray(emptyList())) }).strings("rights"),
        )
    }

    @Test
    fun `a choice list refuses what is not on offer and repeats`() {
        fun parse(vararg entries: String) =
            words.parse(buildJsonObject { put("rights", JsonArray(entries.map(::JsonPrimitive))) })

        assertEquals(listOf("read", "write"), parse("read", "write").strings("rights"))
        assertFailsWith<IllegalArgumentException> { parse("delete") }
        assertFailsWith<IllegalArgumentException> { parse("read", "read") }
    }

    @Test
    fun `a choice takes its default and refuses other words`() {
        assertEquals("fast", one.parse(buildJsonObject {}).string("mode"))
        assertEquals("slow", one.parse(buildJsonObject { put("mode", "slow") }).string("mode"))
        assertFailsWith<IllegalArgumentException> { one.parse(buildJsonObject { put("mode", "warp") }) }
    }

    @Test
    fun `the schemas of the list and choice parameters are valid JSON Schemas`() {
        for (parameters in listOf(ids, words, one)) {
            assertEquals(emptyList(), McpSchema.violationsOfSchema(parameters.schema.toString()))
        }
    }

    @Test
    fun `a string list keeps order and refuses empty and over long input`() {
        val files = ToolParameters(StringListParameter("files", "Some files.", maxItems = 2))
        fun parse(vararg entries: String) =
            files.parse(buildJsonObject { put("files", JsonArray(entries.map(::JsonPrimitive))) })

        assertEquals(listOf("b", "a"), parse("b", "a").strings("files"))
        assertFailsWith<IllegalArgumentException> { parse() }
        assertFailsWith<IllegalArgumentException> { parse("a", "b", "c") }
        assertFailsWith<IllegalArgumentException> { parse("x".repeat(1025)) }
    }

    @Test
    fun `a user parameter takes usernames and ids and nothing else`() {
        val user = ToolParameters(UserParameter())
        fun parse(value: String) = user.parse(buildJsonObject { put("user", value) }).string("user")

        assertEquals("@ada_l", parse(" @ada_l "))
        assertEquals("12345", parse("12345"))
        listOf("me", "@me", "ada", "-100123", "https://t.me/ada", "t.me/+abc", "@a", "+15550100", "").forEach {
            assertFailsWith<IllegalArgumentException>(it) { parse(it) }
        }
    }
}
