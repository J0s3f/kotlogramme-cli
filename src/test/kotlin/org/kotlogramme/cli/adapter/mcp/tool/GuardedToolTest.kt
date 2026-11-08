package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonObject
import org.kotlogramme.cli.adapter.mcp.EchoTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardedToolTest {
    private val none = JsonObject(emptyMap())
    private val explain: (Exception) -> String? = { (it as? IllegalStateException)?.message }

    @Test
    fun `a failure the explainer knows becomes a tool error`() {
        val tool = GuardedTool(EchoTool { _, _ -> throw IllegalStateException("flood wait 30s") }, explain)

        val result = tool.call(none, ToolContext())

        assertTrue(result.isError)
        assertEquals("flood wait 30s", result.text)
    }

    @Test
    fun `a failure the explainer does not know stays an exception`() {
        val tool = GuardedTool(EchoTool { _, _ -> throw UnsupportedOperationException("bug") }, explain)

        assertFailsWith<UnsupportedOperationException> { tool.call(none, ToolContext()) }
    }

    @Test
    fun `rejected arguments are left for the server to report`() {
        val tool = GuardedTool(EchoTool { _, _ -> throw IllegalArgumentException("bad limit") }, explain)

        assertFailsWith<IllegalArgumentException> { tool.call(none, ToolContext()) }
    }

    @Test
    fun `a successful call and the tool's description pass through`() {
        val tool = GuardedTool(EchoTool(), explain)

        assertEquals("echo: ", tool.call(none, ToolContext()).text)
        assertEquals("echo", tool.name)
        assertEquals(AccessLevel.READ_ONLY, tool.access)
    }
}
