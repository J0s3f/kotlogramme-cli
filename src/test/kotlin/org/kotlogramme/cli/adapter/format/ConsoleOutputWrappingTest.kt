package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleOutputWrappingTest {
    private val headers = listOf("id", "text")
    private val rows = listOf(listOf("1", "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda"))

    private fun render(format: OutputFormat, width: () -> Int?): List<String> {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8), width).table(headers, rows)
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd().lines()
    }

    @Test
    fun `without a terminal a table is as wide as its content`() {
        val lines = render(OutputFormat.TABLE) { null }

        assertEquals(5, lines.size)
        assertTrue(lines.first().length > 60, lines.first())
    }

    @Test
    fun `on a terminal a table is wrapped to its width`() {
        val lines = render(OutputFormat.TABLE) { 40 }

        assertTrue(lines.size > 5, lines.toString())
        assertTrue(lines.all { it.length <= 40 }, lines.toString())
    }

    @Test
    fun `plain output is never wrapped`() {
        val lines = render(OutputFormat.PLAIN) { 20 }

        assertEquals(listOf("id\ttext", "1\t${rows[0][1]}"), lines)
    }

    @Test
    fun `json output is never wrapped`() {
        val lines = render(OutputFormat.JSON) { 20 }

        assertEquals(1, lines.size)
        assertTrue(lines.single().contains(rows[0][1]))
    }

    @Test
    fun `a width of zero or less means the width is unknown`() {
        assertEquals(5, render(OutputFormat.TABLE) { 0 }.size)
        assertEquals(5, render(OutputFormat.TABLE) { -1 }.size)
    }

    @Test
    fun `the width is read again for every table`() {
        var width = 40
        val buffer = ByteArrayOutputStream()
        val output = ConsoleOutput(OutputFormat.TABLE, PrintStream(buffer, true, Charsets.UTF_8)) { width }

        output.table(headers, rows)
        val narrow = buffer.toString(Charsets.UTF_8).lines().count { it.startsWith("+") }
        buffer.reset()
        width = 200
        output.table(headers, rows)
        val wide = buffer.toString(Charsets.UTF_8).lines().filter(String::isNotEmpty)

        assertTrue(narrow >= 3)
        assertEquals(5, wide.size, wide.toString())
    }

    @Test
    fun `a writer based output wraps too`() {
        val writer = StringWriter()

        ConsoleOutput(OutputFormat.TABLE, writer) { 40 }.table(headers, rows)

        val lines = writer.toString().replace("\r\n", "\n").trimEnd().lines()
        assertTrue(lines.size > 5 && lines.all { it.length <= 40 }, lines.toString())
    }

    @Test
    fun `a title is printed above the table unwrapped`() {
        val buffer = ByteArrayOutputStream()
        val title = "A title that is longer than the width the table is wrapped to, on purpose"

        ConsoleOutput(OutputFormat.TABLE, PrintStream(buffer, true, Charsets.UTF_8)) { 30 }.table(headers, rows, title)

        assertEquals(title, buffer.toString(Charsets.UTF_8).lines().first())
    }

    @Test
    fun `a failing width lookup does not fail the table`() {
        val buffer = ByteArrayOutputStream()
        val output = ConsoleOutput(OutputFormat.TABLE, PrintStream(buffer, true, Charsets.UTF_8)) {
            error("the terminal went away")
        }

        output.table(headers, rows)

        assertEquals(5, buffer.toString(Charsets.UTF_8).lines().count { it.isNotEmpty() })
    }
}
