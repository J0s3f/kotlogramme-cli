package org.kotlogramme.cli.adapter.mcp.tool

import org.kotlogramme.cli.adapter.mcp.EchoTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToolCatalogTest {
    @Test
    fun `tools are listed in a fixed order by name`() {
        val catalog = ToolCatalog(listOf(EchoTool("zeta"), EchoTool("Alpha"), EchoTool("beta")))

        assertEquals(listOf("Alpha", "beta", "zeta"), catalog.tools.map { it.name })
    }

    @Test
    fun `a tool is found by its exact name`() {
        val echo = EchoTool("echo")
        val catalog = ToolCatalog(listOf(echo))

        assertSame(echo, catalog.find("echo"))
        assertNull(catalog.find("Echo"))
        assertNull(catalog.find("missing"))
    }

    @Test
    fun `two tools with one name are rejected`() {
        assertFailsWith<IllegalArgumentException> { ToolCatalog(listOf(EchoTool("same"), EchoTool("same"))) }
    }

    @Test
    fun `names follow the protocol's character rules`() {
        for (valid in listOf("a", "get_user", "DATA.export-v2", "x".repeat(128))) {
            ToolCatalog(listOf(EchoTool(valid)))
        }
        for (invalid in listOf("", "has space", "comma,name", "slash/name", "x".repeat(129), "ä")) {
            assertFailsWith<IllegalArgumentException>(invalid) { ToolCatalog(listOf(EchoTool(invalid))) }
        }
    }

    @Test
    fun `an empty catalog is allowed`() {
        assertEquals(emptyList(), ToolCatalog(emptyList()).tools)
    }
}
