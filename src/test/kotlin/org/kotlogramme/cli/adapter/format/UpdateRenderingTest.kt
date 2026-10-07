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
import kotlin.test.assertFalse

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
            "+---------+------+------------+--------------+----------------------+-------+---------+-----------+",
            "| kind    | chat | message_id | from         | time                 | text  | chat_id | sender_id |",
            "+---------+------+------------+--------------+----------------------+-------+---------+-----------+",
            "| message | Ada  | 7          | Ada Lovelace | 2026-01-01T12:30:00Z | hello | 1       |           |",
            "+---------+------+------------+--------------+----------------------+-------+---------+-----------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `a plain update is a tab separated row`() {
        val rendered = render(OutputFormat.PLAIN) { renderUpdate(newMessage) }

        assertEquals(
            listOf(
                "kind\tchat\tmessage_id\tfrom\ttime\ttext\tchat_id\tsender_id",
                "message\tAda\t7\tAda Lovelace\t2026-01-01T12:30:00Z\thello\t1",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `an update named by kind only carries that kind`() {
        assertEquals(listOf("typing", "", "", "", "", "", "", ""), updateRow(other))
    }

    @Test
    fun `a message row ends with the ids of its chat and sender`() {
        val withSender = newMessage.copy(message = newMessage.message.copy(senderId = 5))

        assertEquals(listOf("1", "5"), updateRow(withSender).takeLast(2))
    }

    @Test
    fun `a message whose chat is unknown has no chat id`() {
        val noChat = newMessage.copy(chat = null)

        assertEquals(listOf("", ""), updateRow(noChat).takeLast(2))
    }

    @Test
    fun `an update about a user carries that user as the sender id`() {
        val typing = IncomingUpdate.Other("updateUserTyping", userId = 1886794212)

        assertEquals(listOf("", "1886794212"), updateRow(typing).takeLast(2))
    }

    @Test
    fun `the json line carries the ids as strings next to the names`() {
        val withSender = newMessage.copy(message = newMessage.message.copy(senderId = 5))

        val rendered = render(OutputFormat.PLAIN) { renderUpdateJson(withSender) }

        assertEquals(true, rendered.contains(""""chat_id":"1","sender_id":"5""""), rendered)
    }

    @Test
    fun `an update shows its data in the text column`() {
        val status = IncomingUpdate.Other("updateUserStatus", """{"user_id":5}""")

        assertEquals(listOf("updateUserStatus", "", "", "", "", """{"user_id":5}""", "", ""), updateRow(status))
    }

    @Test
    fun `the json line nests the data of an update as an object`() {
        val status = IncomingUpdate.Other("updateUserStatus", """{"user_id":5}""")

        val rendered = render(OutputFormat.PLAIN) { renderUpdateJson(status) }

        assertEquals(
            """{"kind":"updateUserStatus","chat":"","message_id":"","from":"","time":"","text":"",""" +
                    """"chat_id":"","sender_id":"",""" +
                """"data":{"user_id":5}}""",
            rendered,
        )
    }

    @Test
    fun `a json update is an array of one object`() {
        val rendered = render(OutputFormat.JSON) { renderUpdate(newMessage) }

        assertEquals(
            """[{"kind":"message","chat":"Ada","message_id":"7","from":"Ada Lovelace",""" +
                """"time":"2026-01-01T12:30:00Z","text":"hello","chat_id":"1","sender_id":""}]""",
            rendered,
        )
    }

    @Test
    fun `the json line is one object per update`() {
        val rendered = render(OutputFormat.PLAIN) { renderUpdateJson(newMessage) }

        assertEquals(
            """{"kind":"message","chat":"Ada","message_id":"7","from":"Ada Lovelace",""" +
                """"time":"2026-01-01T12:30:00Z","text":"hello","chat_id":"1","sender_id":""}""",
            rendered,
        )
    }


    @Test
    fun `an update with no data has no data field in its json`() {
        val rendered = render(OutputFormat.PLAIN) { renderUpdateJson(other) }

        assertFalse(rendered.contains("\"data\""), rendered)
    }
}
