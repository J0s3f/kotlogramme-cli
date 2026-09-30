package org.kotlogramme.cli.adapter.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.PrintStream

/**
 * Renders command output to a stream in the configured format.
 *
 * Deliberately dependency-free in its rendering: tables are drawn here so the output is identical
 * across terminals and easy to assert in tests. Colour, where it is added later, belongs to a
 * presentation layer above this one.
 */
class ConsoleOutput(
    private val format: OutputFormat,
    private val out: PrintStream = System.out,
) : Output {
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
        val columnCount = maxOf(headers.size, rows.maxOfOrNull(List<String>::size) ?: 0)
        val widths = (0 until columnCount).map { column ->
            (listOf(headers) + rows).maxOf { row -> cell(row, column).length }
        }
        val border = widths.joinToString(separator = "+", prefix = "+", postfix = "+") { "-".repeat(it + 2) }
        line(border)
        line(rowFor(headers, widths))
        line(border)
        rows.forEach { line(rowFor(it, widths)) }
        line(border)
    }

    private fun rowFor(row: List<String>, widths: List<Int>): String =
        widths.indices.joinToString(separator = "|", prefix = "|", postfix = "|") { column ->
            " " + cell(row, column).padEnd(widths[column]) + " "
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
