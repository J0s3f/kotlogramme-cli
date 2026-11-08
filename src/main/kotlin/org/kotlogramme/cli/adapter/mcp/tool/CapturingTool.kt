package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonObject
import org.kotlogramme.cli.application.port.spi.Output

/**
 * A tool that parses its arguments with its declared [parameters], lets [run] print into a
 * [CapturedOutput] with the same renderers the command line uses, and returns that as text and
 * structured content.
 *
 * What it may do with the account is fixed by the subclass through [access] and [annotations]:
 * [ReadingTool], [WritingTool] and [DestructiveTool] are the three levels.
 */
abstract class CapturingTool(
    final override val name: String,
    final override val title: String,
    final override val description: String,
    private val parameters: ToolParameters,
    final override val access: AccessLevel,
    final override val annotations: ToolAnnotations,
) : McpTool {
    final override val inputSchema: JsonObject = parameters.schema

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

/** A tool that only looks at the account. */
abstract class ReadingTool(
    name: String,
    title: String,
    description: String,
    parameters: ToolParameters,
) : CapturingTool(
    name,
    title,
    description,
    parameters,
    AccessLevel.READ_ONLY,
    ToolAnnotations(readOnly = true),
)

/**
 * A tool that changes the account in a way the person can take back or that only adds something:
 * a message, a reaction, a pin, a read receipt. Offered from [AccessLevel.READ_WRITE] on.
 */
abstract class WritingTool(
    name: String,
    title: String,
    description: String,
    parameters: ToolParameters,
    idempotent: Boolean = false,
) : CapturingTool(
    name,
    title,
    description,
    parameters,
    AccessLevel.READ_WRITE,
    ToolAnnotations(readOnly = false, destructive = false, idempotent = idempotent),
)

/**
 * A tool that removes something or changes who may do what, and cannot be fully taken back: deleted
 * messages, removed members, blocks, admin rights, ended sessions. Offered only at [AccessLevel.FULL].
 */
abstract class DestructiveTool(
    name: String,
    title: String,
    description: String,
    parameters: ToolParameters,
    idempotent: Boolean = false,
) : CapturingTool(
    name,
    title,
    description,
    parameters,
    AccessLevel.FULL,
    ToolAnnotations(readOnly = false, destructive = true, idempotent = idempotent),
)
