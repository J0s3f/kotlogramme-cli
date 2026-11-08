package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonObject
import org.kotlogramme.cli.application.port.spi.Output

/**
 * A tool that only looks at the account.
 *
 * It parses its arguments with its declared [parameters], lets [run] print into a [CapturedOutput] with
 * the same renderers the command line uses, and returns that as text and structured content.
 */
abstract class ReadingTool(
    final override val name: String,
    final override val title: String,
    final override val description: String,
    private val parameters: ToolParameters,
) : McpTool {
    final override val inputSchema: JsonObject = parameters.schema
    final override val access = AccessLevel.READ_ONLY
    final override val annotations = ToolAnnotations(readOnly = true)

    final override fun call(arguments: JsonObject, context: ToolContext): ToolResult {
        val parsed = parameters.parse(arguments)
        val output = CapturedOutput()
        return try {
            run(parsed, output, context)
            ToolResult.json(output.result().toString())
        } catch (failure: ToolFailure) {
            ToolResult.error(failure.message.orEmpty())
        }
    }

    protected abstract fun run(arguments: ToolArguments, output: Output, context: ToolContext)
}
