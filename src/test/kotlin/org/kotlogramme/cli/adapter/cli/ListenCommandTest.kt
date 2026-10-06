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
import kotlin.test.assertTrue

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
    fun `listen hides updates that carry no message`() {
        val fixture = cliFixture(listen = FakeListen(listOf(IncomingUpdate.Other("updateUserStatus"), update)))

        fixture.run("listen")

        assertEquals(
            listOf(
                "kind\u0009chat\u0009message_id\u0009from\u0009time\u0009text",
                "message\u0009Ada\u00097\u0009Ada Lovelace\u00092026-01-01T12:30:00Z\u0009hello",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `listen --all also shows updates that carry no message`() {
        val fixture = cliFixture(listen = FakeListen(listOf(IncomingUpdate.Other("updateUserStatus", "ab12"))))

        fixture.run("listen", "--all")

        assertEquals(
            listOf(
                "kind\u0009chat\u0009message_id\u0009from\u0009time\u0009text",
                "updateUserStatus\u0009\u0009\u0009\u0009\u0009ab12",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `listen --all --once stops at the first update of any kind`() {
        val fixture = cliFixture(listen = FakeListen(listOf(IncomingUpdate.Other("updateUserStatus"), update)))

        fixture.run("listen", "--all", "--once")

        assertEquals(2, fixture.output.lines.size)
    }

    @Test
    fun `listen --all --json nests the data of an update`() {
        val status = IncomingUpdate.Other("updateUserStatus", """{"user_id":5}""")
        val fixture = cliFixture(listen = FakeListen(listOf(status)))

        fixture.run("listen", "--all", "--json")

        assertEquals(
            listOf(
                """{"kind":"updateUserStatus","chat":"","message_id":"","from":"","time":"","text":"",""" +
                    """"data":{"user_id":5}}""",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `listen prints nothing when only hidden updates arrive`() {
        val fixture = cliFixture(listen = FakeListen(listOf(IncomingUpdate.Other("updateUserStatus"))))

        fixture.run("listen")

        assertEquals(emptyList(), fixture.output.lines)
    }

    @Test
    fun `listen --once waits for a message and not for a hidden update`() {
        val fixture = cliFixture(listen = FakeListen(listOf(IncomingUpdate.Other("updateUserStatus"), update, update)))

        fixture.run("listen", "--once")

        assertEquals(
            listOf(
                "kind\u0009chat\u0009message_id\u0009from\u0009time\u0009text",
                "message\u0009Ada\u00097\u0009Ada Lovelace\u00092026-01-01T12:30:00Z\u0009hello",
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

    @Test
    fun `listen stops promptly when the stream stays quiet`() {
        // A source that never delivers an update used to keep the command for the facade's 30 s poll.
        val fixture = cliFixture(listen = QuietListen())

        val elapsed = measureMillis { fixture.run("listen", "--once") }

        assertTrue(elapsed < 2_000, "listen took ${elapsed}ms to stop, which is not prompt")
    }
}

private inline fun measureMillis(body: () -> Unit): Long {
    val before = System.nanoTime()
    body()
    return (System.nanoTime() - before) / 1_000_000
}
