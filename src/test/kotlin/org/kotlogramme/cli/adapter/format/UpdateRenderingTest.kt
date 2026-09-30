package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.cli.domain.Message
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateRenderingTest {
    private val newMessage = IncomingUpdate.NewMessage(
        chat = Chat(
            id = 1,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = null,
            lastMessageAt = null,
        ),
        message = Message(
            id = 7,
            senderName = "Ada Lovelace",
            text = "hello",
            sentAt = Instant.parse("2026-01-01T12:30:00Z"),
            outgoing = false,
        ),
    )

    private val other = IncomingUpdate.Other("typing")

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the chat, message and sender of a new message`() {
        val rendered = render(OutputFormat.TABLE) { renderUpdate(newMessage) }

        val expected = listOf(
            "+---------+------+------------+--------------+----------------------+-------+",
            "| kind    | chat | message_id | from         | time                 | text  |",
            "+---------+------+------------+--------------+----------------------+-------+",
            "| message | Ada  | 7          | Ada Lovelace | 2026-01-01T12:30:00Z | hello |",
            "+---------+------+------------+--------------+----------------------+-------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `a plain update is a tab separated row`() {
        val rendered = render(OutputFormat.PLAIN) { renderUpdate(newMessage) }

        assertEquals(
            listOf(
                "kind\tchat\tmessage_id\tfrom\ttime\ttext",
                "message\tAda\t7\tAda Lovelace\t2026-01-01T12:30:00Z\thello",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `an update named by kind only carries that kind`() {
        assertEquals(listOf("typing", "", "", "", "", ""), updateRow(other))
    }

    @Test
    fun `a json update is an array of one object`() {
        val rendered = render(OutputFormat.JSON) { renderUpdate(newMessage) }

        assertEquals(
            """[{"kind":"message","chat":"Ada","message_id":"7","from":"Ada Lovelace",""" +
                """"time":"2026-01-01T12:30:00Z","text":"hello"}]""",
            rendered,
        )
    }

    @Test
    fun `the json line is one object per update`() {
        val rendered = render(OutputFormat.PLAIN) { renderUpdateJson(newMessage) }

        assertEquals(
            """{"kind":"message","chat":"Ada","message_id":"7","from":"Ada Lovelace",""" +
                """"time":"2026-01-01T12:30:00Z","text":"hello"}""",
            rendered,
        )
    }
}
