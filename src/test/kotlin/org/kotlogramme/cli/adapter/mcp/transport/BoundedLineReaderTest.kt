package org.kotlogramme.cli.adapter.mcp.transport

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoundedLineReaderTest {
    private fun reader(text: String, limit: Int = 1024, chunk: Int = 4096) =
        BoundedLineReader(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), limit, chunk)

    private fun BoundedLineReader.all(): List<Line> = generateSequence { next() }.toList()

    @Test
    fun `lines are split on line feeds`() {
        assertEquals(listOf(Line.Text("a"), Line.Text("b")), reader("a\nb\n").all())
    }

    @Test
    fun `a carriage return before the line feed is dropped`() {
        assertEquals(listOf(Line.Text("a"), Line.Text("b")), reader("a\r\nb\r\n").all())
    }

    @Test
    fun `a last line without a line feed is still delivered`() {
        assertEquals(listOf(Line.Text("a"), Line.Text("b")), reader("a\nb").all())
    }

    @Test
    fun `empty input has no lines`() {
        assertNull(reader("").next())
    }

    @Test
    fun `blank lines are delivered as empty text`() {
        assertEquals(listOf(Line.Text("a"), Line.Text(""), Line.Text("b")), reader("a\n\nb\n").all())
    }

    @Test
    fun `multi byte characters are intact when they straddle a read boundary`() {
        val text = "äöü😀中文"

        for (chunk in 1..7) {
            assertEquals(listOf(Line.Text(text)), reader("$text\n", chunk = chunk).all(), "chunk $chunk")
        }
    }

    @Test
    fun `a line over the limit is reported once and the next line is read normally`() {
        val lines = reader("x".repeat(50) + "\nok\n", limit = 10, chunk = 8).all()

        assertEquals(listOf(Line.TooLong, Line.Text("ok")), lines)
    }

    @Test
    fun `a line exactly at the limit is accepted`() {
        assertEquals(listOf(Line.Text("x".repeat(10))), reader("x".repeat(10) + "\n", limit = 10).all())
    }

    @Test
    fun `an over long last line without a line feed is reported`() {
        assertEquals(listOf(Line.TooLong), reader("x".repeat(50), limit = 10).all())
    }
}
