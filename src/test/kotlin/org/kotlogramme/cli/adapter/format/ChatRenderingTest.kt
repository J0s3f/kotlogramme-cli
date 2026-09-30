package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRenderingTest {
    private val chats = listOf(
        Chat(
            id = 1,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = "hi",
            lastMessageAt = null,
            unreadCount = 3,
            pinned = true,
        ),
        Chat(
            id = 2,
            title = "Team",
            kind = ChatKind.GROUP,
            username = null,
            lastMessagePreview = "bye",
            lastMessageAt = null,
        ),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the unread count and the pinned marker`() {
        val rendered = render(OutputFormat.TABLE) { renderChats(chats) }

        val expected = listOf(
            "+----+---------+-------+--------+--------+------+",
            "| id | kind    | title | unread | pinned | last |",
            "+----+---------+-------+--------+--------+------+",
            "| 1  | private | Ada   | 3      | yes    | hi   |",
            "| 2  | group   | Team  | 0      |        | bye  |",
            "+----+---------+-------+--------+--------+------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain chats are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderChats(chats) }

        assertEquals(
            listOf(
                "id\tkind\ttitle\tunread\tpinned\tlast",
                "1\tprivate\tAda\t3\tyes\thi",
                "2\tgroup\tTeam\t0\t\tbye",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json chats are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderChats(chats) }

        assertEquals(
            """[{"id":"1","kind":"private","title":"Ada","unread":"3","pinned":"yes","last":"hi"},""" +
                """{"id":"2","kind":"group","title":"Team","unread":"0","pinned":"","last":"bye"}]""",
            rendered,
        )
    }
}
