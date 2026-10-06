package org.kotlogramme.cli.adapter.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Writer

/**
 * Renders command output to a stream in the configured format.
 *
 * Deliberately dependency-free in its rendering: tables are drawn here so the output is identical
 * across terminals and easy to assert in tests. A cell may already carry ANSI styling from the
 * renderer above; the escapes are ignored when a column is measured and padded so the border cannot
 * drift, and nothing here adds styling of its own.
 *
 * Two sinks are accepted, and the rendering is the same for both. A [PrintStream] is the process's
 * own stdout and the fallback wherever there is no terminal. A [Writer] is how the output reaches a
 * JLine terminal without passing through `System.out`: JLine writes with `WriteConsoleW`, which
 * renders non-ASCII correctly at any console code page, where a `System.out` encoder would have
 * turned those characters into `?` before the terminal ever saw them.
 *
 * [terminalWidth] is the width of the terminal the output is shown on, or `null` when it is not a
 * terminal. A table in [OutputFormat.TABLE] is wrapped to fit it; the plain and JSON formats are for
 * programs and are never wrapped. It is a function because a terminal can be resized between two
 * tables.
 */
class ConsoleOutput(
    private val format: OutputFormat,
    private val out: PrintWriter,
    private val terminalWidth: () -> Int? = { null },
) : Output {
    /**
     * Renders to [out], preserving the stream's own charset so a redirected run stays byte-identical
     * to what `System.out` would have written.
     */
    constructor(
        format: OutputFormat,
        out: PrintStream = System.out,
        terminalWidth: () -> Int? = { null },
    ) : this(format, PrintWriter(out, true, out.charset()), terminalWidth)

    /** Renders to a [writer], such as a JLine terminal's, using the writer's own encoding. */
    constructor(
        format: OutputFormat,
        writer: Writer,
        terminalWidth: () -> Int? = { null },
    ) : this(format, PrintWriter(writer, true), terminalWidth)

    override fun line(text: String) {
        out.println(text)
    }

    override fun table(headers: List<String>, rows: List<List<String>>, title: String?) {
        if (title != null) line(title)
        when (format) {
            OutputFormat.TABLE -> asciiTable(headers, rows)
            OutputFormat.PLAIN -> delimitedTable(headers, rows, separator = "\t")
            OutputFormat.JSON -> jsonTable(headers, rows)
        }
    }

    private fun asciiTable(headers: List<String>, rows: List<List<String>>) {
        val width = runCatching(terminalWidth).getOrNull()?.takeIf { it > 0 }
        renderAsciiTable(headers, rows, width).forEach(::line)
    }

    private fun delimitedTable(headers: List<String>, rows: List<List<String>>, separator: String) {
        line(headers.joinToString(separator))
        rows.forEach { line(it.joinToString(separator)) }
    }

    private fun jsonTable(headers: List<String>, rows: List<List<String>>) {
        val payload = JsonArray(
            rows.map { row ->
                JsonObject(headers.withIndex().associate { (column, header) -> header to JsonPrimitive(cell(row, column)) })
            },
        )
        line(payload.toString())
    }

    private fun cell(row: List<String>, column: Int): String = row.getOrElse(column) { "" }
}
