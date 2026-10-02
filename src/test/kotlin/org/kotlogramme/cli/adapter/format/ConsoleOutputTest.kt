package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `a cell carrying ansi styling is padded by its visible width`() {
        val styled = "\u001B[1mbold\u001B[22m"

        val rendered = render(OutputFormat.TABLE) {
            table(listOf("name"), listOf(listOf(styled)))
        }
        val lines = rendered.lines()

        assertTrue(rendered.contains("\u001B[1m"))
        // The escapes must not count towards the column, or the border would be too wide.
        assertEquals(1, lines.map(::visibleLength).distinct().size)
        assertEquals("| bold |", lines[3].replace(Regex("\u001B\\[[0-9;]*m"), ""))
    }

    @Test
    fun `a title is printed above the table`() {
        val rendered = render(OutputFormat.PLAIN) {
            table(listOf("id"), emptyList(), title = "Chats")
        }

        assertEquals(listOf("Chats", "id"), rendered.lines())
    }

    @Test
    fun `non-ascii text survives a terminal writer unchanged`() {
        // The terminal path takes a Writer rather than a stream. A StringWriter stands in for
        // JLine's: what matters here is that no encoder in between replaces the characters, which is
        // exactly what the process's own stdout does at a legacy console code page.
        val text = "Привет, мир! 你好 مرحبا 😀🎉"

        val writer = StringWriter()
        ConsoleOutput(OutputFormat.PLAIN, writer).line(text)

        assertEquals(text, writer.toString().trimEnd('\n', '\r'))
    }

    @Test
    fun `a table with non-ascii cells reaches a writer unchanged`() {
        val rendered = renderWriter(OutputFormat.TABLE) {
            table(listOf("name"), listOf(listOf("😀 emoji"), listOf("x")))
        }
        val lines = rendered.lines()

        assertTrue(rendered.contains("😀 emoji"), "expected the emoji in $rendered")
        // Padding is measured the same way for every line, so a multi-unit emoji cannot drift the
        // border here; it is the writer that must not mangle the characters.
        assertEquals(1, lines.map(::visibleLength).distinct().size)
    }

    @Test
    fun `a table lines its columns up by display width when cells hold wide characters`() {
        val rendered = render(OutputFormat.TABLE) {
            table(
                listOf("name", "note"),
                listOf(
                    listOf("plain", "abc"),
                    listOf("cjk", "\u4f60\u597d\u4e16\u754c"),
                    listOf("emoji", "\ud83d\ude00\ud83d\ude80"),
                    listOf("combining", "e\u0301e\u0301"),
                    listOf("flag", "\ud83c\udde6\ud83c\uddf9"),
                    listOf("zwj", "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc67"),
                ),
            )
        }

        // The border, the header and every row must occupy the same number of terminal columns, which
        // is what visibleLength measures: a code-unit count leaves each wide-character row a different
        // width, the drift a live dialog list showed.
        val widths = rendered.lines().map(::visibleLength).distinct()
        assertEquals(1, widths.size, "the table's lines have different display widths: $widths")
    }

    private fun renderWriter(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = StringWriter()
        ConsoleOutput(format, buffer).block()
        return buffer.toString().replace("\r\n", "\n").trimEnd()
    }
}
