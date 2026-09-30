package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleOutputTest {
    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table pads every column to its widest cell`() {
        val rendered = render(OutputFormat.TABLE) {
            table(listOf("id", "name"), listOf(listOf("1", "a longer name"), listOf("22", "x")))
        }

        val idWidth = "id".length
        val nameWidth = "a longer name".length
        val border = "+" + "-".repeat(idWidth + 2) + "+" + "-".repeat(nameWidth + 2) + "+"
        val expected = listOf(
            border,
            "| " + "id".padEnd(idWidth) + " | " + "name".padEnd(nameWidth) + " |",
            border,
            "| " + "1".padEnd(idWidth) + " | " + "a longer name".padEnd(nameWidth) + " |",
            "| " + "22".padEnd(idWidth) + " | " + "x".padEnd(nameWidth) + " |",
            border,
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `a plain table joins the cells with tabs`() {
        val rendered = render(OutputFormat.PLAIN) {
            table(listOf("id", "name"), listOf(listOf("1", "josef")))
        }

        assertEquals(listOf("id\tname", "1\tjosef"), rendered.lines())
    }

    @Test
    fun `a json table is an array of objects keyed by the headers`() {
        val rendered = render(OutputFormat.JSON) {
            table(listOf("id", "name"), listOf(listOf("1", "josef")))
        }

        assertEquals("""[{"id":"1","name":"josef"}]""", rendered)
    }

    @Test
    fun `a row shorter than the headers is padded with empty cells`() {
        val rendered = render(OutputFormat.JSON) {
            table(listOf("id", "name"), listOf(listOf("1")))
        }

        assertEquals("""[{"id":"1","name":""}]""", rendered)
    }

    @Test
    fun `a title is printed above the table`() {
        val rendered = render(OutputFormat.PLAIN) {
            table(listOf("id"), emptyList(), title = "Chats")
        }

        assertEquals(listOf("Chats", "id"), rendered.lines())
    }
}
