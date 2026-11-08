package org.kotlogramme.cli.adapter.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The schema checks only mean something if they can fail, so this shows that they do. */
class McpSchemaOracleTest {
    @Test
    fun `a result missing its required fields is rejected`() {
        assertTrue(McpSchema.violations(MODERN, "DiscoverResult", "{}").isNotEmpty())
        assertTrue(McpSchema.violations(MODERN, "ListToolsResult", """{"resultType":"complete","tools":[]}""").isNotEmpty())
    }

    @Test
    fun `an error response with a null id is rejected`() {
        val nullId = """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"x"}}"""

        assertTrue(McpSchema.violations(MODERN, "JSONRPCErrorResponse", nullId).isNotEmpty())
    }

    @Test
    fun `a tool without an input schema is rejected`() {
        val tool = """{"resultType":"complete","tools":[{"name":"x"}],"ttlMs":1,"cacheScope":"public"}"""

        assertTrue(McpSchema.violations(MODERN, "ListToolsResult", tool).isNotEmpty())
    }

    @Test
    fun `a document that is not a JSON Schema is rejected`() {
        assertTrue(McpSchema.violationsOfSchema("""{"type": 5}""").isNotEmpty())
        assertEquals(emptyList(), McpSchema.violationsOfSchema("""{"type":"object","additionalProperties":false}"""))
    }

    @Test
    fun `a valid discover result passes`() {
        val valid = """{"resultType":"complete","supportedVersions":["2026-07-28"],"capabilities":{},"ttlMs":0,"cacheScope":"public"}"""

        assertEquals(emptyList(), McpSchema.violations(MODERN, "DiscoverResult", valid))
    }
}
