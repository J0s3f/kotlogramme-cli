package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.MessageEntity
import org.kotlogramme.cli.domain.MessageQuote
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            viaBotId = 99,
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
            "+----+----------------------+--------------+-----+-------+-------+---------+------------------+------+",
            "| id | time                 | from         | via | reply | quote | media   | action           | text |",
            "+----+----------------------+--------------+-----+-------+-------+---------+------------------+------+",
            "| 7  | 2026-01-01T12:30:00Z | Ada Lovelace |     | 5     |       | [photo] |                  | look |",
            "| 8  | 2026-01-01T12:31:00Z | Bob          | 99  |       |       |         |                  | ok   |",
            "| 9  | 2026-01-01T12:32:00Z | Ada Lovelace |     |       |       |         | pinned a message |      |",
            "+----+----------------------+--------------+-----+-------+-------+---------+------------------+------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain messages are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderMessages(messages) }

        assertEquals(
            listOf(
                "id\ttime\tfrom\tvia\treply\tquote\tmedia\taction\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t\t5\t\t[photo]\t\tlook",
                "8\t2026-01-01T12:31:00Z\tBob\t99\t\t\t\t\tok",
                "9\t2026-01-01T12:32:00Z\tAda Lovelace\t\t\t\t\tpinned a message",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json messages are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderMessages(messages) }

        assertEquals(
            """[{"id":"7","time":"2026-01-01T12:30:00Z","from":"Ada Lovelace","via":"",""" +
                """"reply":"5","quote":"","media":"[photo]","action":"","text":"look"},""" +
                """{"id":"8","time":"2026-01-01T12:31:00Z","from":"Bob","via":"99",""" +
                """"reply":"","quote":"","media":"","action":"","text":"ok"},""" +
                """{"id":"9","time":"2026-01-01T12:32:00Z","from":"Ada Lovelace","via":"",""" +
                """"reply":"","quote":"","media":"","action":"pinned a message","text":""}]""",
            rendered,
        )
    }

    @Test
    fun `a styled message stays aligned with an unstyled one`() {
        val plain = Message(
            id = 1,
            senderName = "Ada",
            text = "hello bold world",
            sentAt = Instant.EPOCH,
            outgoing = false,
        )
        val styled = plain.copy(entities = listOf(MessageEntity("bold", 6, 4)))

        val rendered = render(OutputFormat.TABLE) {
            renderMessages(listOf(plain, styled), MessageStyler.table(color = true))
        }
        val lines = rendered.lines()

        assertTrue(rendered.contains("\u001B[1m"), rendered)
        // The escapes must not count towards a column, or every border would drift.
        assertEquals(1, lines.map(::visibleLength).distinct().size, rendered)
        assertEquals(lines[3], lines[4].replace(Regex("\u001B\\[[0-9;]*m"), ""))
    }

    @Test
    fun `a reply shows its quoted text`() {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "sure",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
            quote = MessageQuote("where is the file?"),
        )

        assertEquals("\"where is the file?\"", quoteLabel(message.quote!!))
        assertTrue(render(OutputFormat.TABLE) { renderMessages(listOf(message)) }.contains("\"where is the file?\""))
    }

    @Test
    fun `the entities inside a quote are styled like any other entity`() {
        val plain = Message(
            id = 1,
            senderName = "Ada",
            text = "",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
            quote = MessageQuote("please see the file", listOf(MessageEntity("bold", 11, 4))),
        )

        val styled = render(OutputFormat.TABLE) {
            renderMessages(listOf(plain), MessageStyler.table(color = true))
        }
        assertTrue(styled.contains("\u001B[1m"), styled)

        val pipeless = render(OutputFormat.TABLE) {
            renderMessages(listOf(plain), MessageStyler.table(color = false))
        }
        assertFalse(pipeless.contains("\u001B"), pipeless)
        assertTrue(pipeless.contains("\"please see the file\""), pipeless)
    }

    @Test
    fun `a quote whose entities are styled keeps the table aligned`() {
        val plain = Message(
            id = 1,
            senderName = "Ada",
            text = "hi",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
            quote = MessageQuote("plain quote"),
        )
        val styled = plain.copy(quote = MessageQuote("bold quote", listOf(MessageEntity("bold", 0, 4))))

        val rendered = render(OutputFormat.TABLE) {
            renderMessages(listOf(plain, styled), MessageStyler.table(color = true))
        }
        val lines = rendered.lines()

        assertTrue(rendered.contains("\u001B[1m"), rendered)
        // A styled quote must not count its escapes towards the column.
        assertEquals(1, lines.map(::visibleLength).distinct().size, rendered)
    }

    @Test
    fun `a non-reply renders unchanged`() {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "hello",
            sentAt = Instant.EPOCH,
            outgoing = false,
        )

        val rendered = render(OutputFormat.TABLE) { renderMessages(listOf(message)) }

        assertFalse(rendered.contains("\"\""), rendered)
        // The new, always-present quote column is empty; every other cell is what it was.
        assertEquals(
            listOf(
                "1", "1970-01-01T00:00:00Z", "Ada", "", "", "", "", "", "hello",
            ),
            messageRow(message),
        )
    }

    @Test
    fun `a reply whose header carries no text shows no quote`() {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "ok",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
        )

        val rendered = render(OutputFormat.TABLE) { renderMessages(listOf(message)) }

        // A reply with an absent quote renders no empty brackets and no empty string.
        assertFalse(rendered.contains("\""), rendered)
        assertEquals("""5""", messageRow(message)[4])
        assertEquals("", messageRow(message)[5])
    }

    @Test
    fun `a quote containing a newline does not break the table`() {
        val plain = Message(
            id = 1,
            senderName = "Ada",
            text = "",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
            quote = MessageQuote("line one"),
        )
        val multiline = plain.copy(quote = MessageQuote("line one\nline two"))

        val rendered = render(OutputFormat.TABLE) { renderMessages(listOf(plain, multiline), MessageStyler.table(color = true)) }
        val lines = rendered.lines()

        // One table is six lines (three borders and three rows); a newline would push it past that.
        assertEquals(6, lines.size, rendered)
        assertEquals(1, lines.map(::visibleLength).distinct().size, rendered)
        assertTrue(rendered.contains("\"line one line two\""), rendered)
    }

    @Test
    fun `a very long quote is truncated and the table still lines up`() {
        val long = (1..200).joinToString(" ") { "word" }
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "",
            sentAt = Instant.EPOCH,
            outgoing = false,
            replyToMessageId = 5,
            quote = MessageQuote(long),
        )

        val rendered = render(OutputFormat.TABLE) { renderMessages(listOf(message), MessageStyler.table(color = true)) }
        val lines = rendered.lines()
        val label = quoteLabel(message.quote!!)

        assertEquals(1, lines.map(::visibleLength).distinct().size, rendered)
        assertTrue(label.endsWith("…\""), label)
        // Two quotes plus the visible truncation limit (ellipsis included).
        assertEquals(82, label.length, label)
        assertEquals(80, label.removeSurrounding("\"").length, label)
    }

    @Test
    fun `colour off renders the same row without escapes`() {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "hello bold world",
            sentAt = Instant.EPOCH,
            outgoing = false,
            entities = listOf(MessageEntity("bold", 6, 4)),
        )

        val rendered = render(OutputFormat.TABLE) {
            renderMessages(listOf(message), MessageStyler.table(color = false))
        }

        assertFalse(rendered.contains("\u001B"), rendered)
        assertTrue(rendered.contains("hello bold world"), rendered)
    }

    @Test
    fun `json carries the raw text without escapes`() {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "hi",
            sentAt = Instant.EPOCH,
            outgoing = false,
            entities = listOf(MessageEntity("bold", 0, 2), MessageEntity("spoiler", 0, 2)),
        )

        val rendered = render(OutputFormat.JSON) { renderMessages(listOf(message)) }

        assertFalse(rendered.contains("\u001B"), rendered)
        assertTrue(rendered.contains("\"text\":\"hi\""), rendered)
    }

    @Test
    fun `the via column shows the inline bot username, the bare id or blank`() {
        val base = Message(
            id = 1,
            senderName = "Ada",
            text = "",
            sentAt = Instant.EPOCH,
            outgoing = false,
        )

        assertEquals(
            "@inline_bot",
            viaBotLabel(base.copy(viaBotId = 99, viaBotUsername = "inline_bot")),
        )
        assertEquals("99", viaBotLabel(base.copy(viaBotId = 99)))
        assertEquals("", viaBotLabel(base))
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
    fun `a video label prefers the resolution over the thumbnail dimensions`() {
        assertEquals(
            "[video 0:03 1920x1080]",
            mediaLabel(
                MediaInfo(
                    "video",
                    durationSeconds = 3.0,
                    width = 320,
                    height = 240,
                    resolutionWidth = 1920,
                    resolutionHeight = 1080,
                ),
            ),
        )
        assertEquals(
            "[animation 640x480]",
            mediaLabel(
                MediaInfo(
                    "animation",
                    width = 160,
                    height = 120,
                    resolutionWidth = 640,
                    resolutionHeight = 480,
                ),
            ),
        )
    }

    @Test
    fun `a video falls back to the thumbnail dimensions without a resolution`() {
        assertEquals(
            "[video 0:03 320x240]",
            mediaLabel(MediaInfo("video", durationSeconds = 3.0, width = 320, height = 240)),
        )
    }

    @Test
    fun `a video with neither a resolution nor thumbnail dimensions shows only its kind`() {
        assertEquals("[video 0:03]", mediaLabel(MediaInfo("video", durationSeconds = 3.0)))
        assertEquals("[video]", mediaLabel(MediaInfo("video")))
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
