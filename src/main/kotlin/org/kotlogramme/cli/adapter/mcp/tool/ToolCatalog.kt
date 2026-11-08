package org.kotlogramme.cli.adapter.mcp.tool

/**
 * The tools a server offers, in the one fixed order clients can cache.
 *
 * Tools are ordered by name so the list is identical from one call to the next, which the specification asks
 * for and which keeps a language model's prompt cache valid.
 */
class ToolCatalog(tools: List<McpTool>) {
    val tools: List<McpTool> = tools.sortedBy { it.name }

    private val byName: Map<String, McpTool>

    init {
        this.tools.forEach { require(NAME.matches(it.name)) { "Invalid tool name '${it.name}': use 1 to 128 of A-Z a-z 0-9 _ - ." } }
        val duplicates = this.tools.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate tool names: ${duplicates.joinToString()}" }
        byName = this.tools.associateBy { it.name }
    }

    fun find(name: String): McpTool? = byName[name]

    private companion object {
        val NAME = Regex("[A-Za-z0-9_.-]{1,128}")
    }
}
