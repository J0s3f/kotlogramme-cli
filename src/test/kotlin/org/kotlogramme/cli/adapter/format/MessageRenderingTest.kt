package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Message
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageRenderingTest {
    private val messages = listOf(
        Message(
            id = 7,
            senderName = "Ada Lovelace",
            text = "look",
            sentAt = Instant.parse("2026-01-01T12:30:00Z"),
            outgoing = true,
            replyToMessageId = 5,
            mediaKind = "photo",
        ),
        Message(
            id = 8,
            senderName = "Bob",
            text = "ok",
            sentAt = Instant.parse("2026-01-01T12:31:00Z"),
            outgoing = false,
        ),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the sender, time, reply marker and media placeholder`() {
        val rendered = render(OutputFormat.TABLE) { renderMessages(messages) }

        val expected = listOf(
            "+----+----------------------+--------------+-------+---------+------+",
            "| id | time                 | from         | reply | media   | text |",
            "+----+----------------------+--------------+-------+---------+------+",
            "| 7  | 2026-01-01T12:30:00Z | Ada Lovelace | 5     | [photo] | look |",
            "| 8  | 2026-01-01T12:31:00Z | Bob          |       |         | ok   |",
            "+----+----------------------+--------------+-------+---------+------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain messages are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderMessages(messages) }

        assertEquals(
            listOf(
                "id\ttime\tfrom\treply\tmedia\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t5\t[photo]\tlook",
                "8\t2026-01-01T12:31:00Z\tBob\t\t\tok",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json messages are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderMessages(messages) }

        assertEquals(
            """[{"id":"7","time":"2026-01-01T12:30:00Z","from":"Ada Lovelace","reply":"5",""" +
                """"media":"[photo]","text":"look"},""" +
                """{"id":"8","time":"2026-01-01T12:31:00Z","from":"Bob","reply":"","media":"","text":"ok"}]""",
            rendered,
        )
    }
}
