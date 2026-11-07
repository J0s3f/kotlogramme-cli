package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** Everything a command says besides a table has a text form and a JSON form. */
class ConsoleOutputStatementsTest {
    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a message is a line in the table and plain formats`() {
        assertEquals("Blocked @ada.", render(OutputFormat.TABLE) { message("Blocked @ada.") })
        assertEquals("Blocked @ada.", render(OutputFormat.PLAIN) { message("Blocked @ada.") })
    }

    @Test
    fun `a message is an object in the json format`() {
        assertEquals("""{"message":"Blocked @ada."}""", render(OutputFormat.JSON) { message("Blocked @ada.") })
    }

    @Test
    fun `a message escapes what json needs escaped`() {
        assertEquals("""{"message":"say \"hi\""}""", render(OutputFormat.JSON) { message("say \"hi\"") })
    }

    @Test
    fun `the next page is a comment line in the table and plain formats`() {
        assertEquals("# next: --after 42", render(OutputFormat.TABLE) { nextPage("42") })
        assertEquals("# next: --after 42", render(OutputFormat.PLAIN) { nextPage("42") })
    }

    @Test
    fun `the next page is an object in the json format`() {
        assertEquals("""{"next_after":"42"}""", render(OutputFormat.JSON) { nextPage("42") })
    }

    @Test
    fun `a count is the bare number in the table and plain formats`() {
        assertEquals("17", render(OutputFormat.TABLE) { count(17) })
        assertEquals("17", render(OutputFormat.PLAIN) { count(17) })
    }

    @Test
    fun `a count is an object in the json format`() {
        assertEquals("""{"count":17}""", render(OutputFormat.JSON) { count(17) })
    }

    @Test
    fun `a record joins its values with tabs in the table and plain formats`() {
        val fields = listOf("path" to "a.png", "size" to "12")

        assertEquals("a.png\t12", render(OutputFormat.TABLE) { record(fields) })
        assertEquals("a.png\t12", render(OutputFormat.PLAIN) { record(fields) })
    }

    @Test
    fun `a record is an object in the json format`() {
        val fields = listOf("path" to "a.png", "size" to "12")

        assertEquals("""{"path":"a.png","size":"12"}""", render(OutputFormat.JSON) { record(fields) })
    }

    @Test
    fun `properties are labelled lines in the table format`() {
        val properties = listOf("Session path" to "s.sqlite", "Output format" to "table")

        assertEquals(
            "Session path: s.sqlite\nOutput format: table",
            render(OutputFormat.TABLE) { properties(properties) },
        )
    }

    @Test
    fun `properties are tab separated pairs in the plain format`() {
        val properties = listOf("Session path" to "s.sqlite", "Output format" to "table")

        assertEquals(
            "session_path\ts.sqlite\noutput_format\ttable",
            render(OutputFormat.PLAIN) { properties(properties) },
        )
    }

    @Test
    fun `properties are one object with snake case keys in the json format`() {
        val properties = listOf("Session path" to "s.sqlite", "Output format" to "table")

        assertEquals(
            """{"session_path":"s.sqlite","output_format":"table"}""",
            render(OutputFormat.JSON) { properties(properties) },
        )
    }
}
