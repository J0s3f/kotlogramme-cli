package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.MediaInfo
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
            media = MediaInfo("photo"),
        ),
        Message(
            id = 8,
            senderName = "Bob",
            text = "ok",
            sentAt = Instant.parse("2026-01-01T12:31:00Z"),
            outgoing = false,
        ),
        Message(
            id = 9,
            senderName = "Ada Lovelace",
            text = "",
            sentAt = Instant.parse("2026-01-01T12:32:00Z"),
            outgoing = false,
            action = "pinned a message",
        ),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the sender, time, reply marker, media placeholder and service action`() {
        val rendered = render(OutputFormat.TABLE) { renderMessages(messages) }

        val expected = listOf(
            "+----+----------------------+--------------+-------+---------+------------------+------+",
            "| id | time                 | from         | reply | media   | action           | text |",
            "+----+----------------------+--------------+-------+---------+------------------+------+",
            "| 7  | 2026-01-01T12:30:00Z | Ada Lovelace | 5     | [photo] |                  | look |",
            "| 8  | 2026-01-01T12:31:00Z | Bob          |       |         |                  | ok   |",
            "| 9  | 2026-01-01T12:32:00Z | Ada Lovelace |       |         | pinned a message |      |",
            "+----+----------------------+--------------+-------+---------+------------------+------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain messages are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderMessages(messages) }

        assertEquals(
            listOf(
                "id\ttime\tfrom\treply\tmedia\taction\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t5\t[photo]\t\tlook",
                "8\t2026-01-01T12:31:00Z\tBob\t\t\t\tok",
                "9\t2026-01-01T12:32:00Z\tAda Lovelace\t\t\tpinned a message",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json messages are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderMessages(messages) }

        assertEquals(
            """[{"id":"7","time":"2026-01-01T12:30:00Z","from":"Ada Lovelace","reply":"5",""" +
                """"media":"[photo]","action":"","text":"look"},""" +
                """{"id":"8","time":"2026-01-01T12:31:00Z","from":"Bob","reply":"","media":"",""" +
                """"action":"","text":"ok"},""" +
                """{"id":"9","time":"2026-01-01T12:32:00Z","from":"Ada Lovelace","reply":"",""" +
                """"media":"","action":"pinned a message","text":""}]""",
            rendered,
        )
    }

    @Test
    fun `media labels carry the details the kind has`() {
        assertEquals(
            "[video 0:03 320x240]",
            mediaLabel(MediaInfo("video", durationSeconds = 3.0, width = 320, height = 240)),
        )
        assertEquals(
            "[animation 1:23 640x480]",
            mediaLabel(MediaInfo("animation", durationSeconds = 83.0, width = 640, height = 480)),
        )
        assertEquals("[audio 3:21]", mediaLabel(MediaInfo("audio", durationSeconds = 201.0)))
        assertEquals("[voice 0:07]", mediaLabel(MediaInfo("voice", durationSeconds = 7.0)))
        assertEquals("[photo 320x240]", mediaLabel(MediaInfo("photo", width = 320, height = 240)))
        assertEquals("[document 1.6 MB]", mediaLabel(MediaInfo("document", sizeBytes = 1_677_722)))
        assertEquals("[sticker]", mediaLabel(MediaInfo("sticker")))
    }

    @Test
    fun `a missing detail is left out of the label`() {
        assertEquals("[video]", mediaLabel(MediaInfo("video")))
        assertEquals("[photo]", mediaLabel(MediaInfo("photo", width = 320)))
        assertEquals("[document]", mediaLabel(MediaInfo("document")))
    }

    @Test
    fun `a duration reaching an hour is hours minutes and seconds`() {
        assertEquals("0:03", formatDuration(3.4))
        assertEquals("59:59", formatDuration(3599.0))
        assertEquals("1:00:00", formatDuration(3600.0))
        assertEquals("1:02:03", formatDuration(3723.0))
        assertEquals("43:00", formatDuration(2580.0))
    }
}
