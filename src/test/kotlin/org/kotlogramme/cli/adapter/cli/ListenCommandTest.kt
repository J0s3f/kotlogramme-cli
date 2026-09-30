package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.cli.domain.Message
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ListenCommandTest {
    private val update = IncomingUpdate.NewMessage(
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

    @Test
    fun `listen --once prints the first update and stops`() {
        val fixture = cliFixture(listen = FakeListen(listOf(update, update)))

        val result = fixture.run("listen", "--once")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "kind\tchat\tmessage_id\tfrom\ttime\ttext",
                "message\tAda\t7\tAda Lovelace\t2026-01-01T12:30:00Z\thello",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `listen --json prints one JSON object per update`() {
        val fixture = cliFixture(listen = FakeListen(listOf(update)))

        val result = fixture.run("listen", "--once", "--json")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                """{"kind":"message","chat":"Ada","message_id":"7","from":"Ada Lovelace",""" +
                    """"time":"2026-01-01T12:30:00Z","text":"hello"}""",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `listen reports missing credentials before starting the stream`() {
        val fixture = cliFixture(
            config = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite")),
            listen = FakeListen(listOf(update)),
        )

        val result = fixture.run("listen")

        assertEquals(1, result.statusCode)
        assertEquals(emptyList(), fixture.output.lines)
    }
}
