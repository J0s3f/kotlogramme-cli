package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FileRenderingTest {
    private val files = listOf(
        Message(
            id = 7,
            senderName = "Ada Lovelace",
            text = "",
            sentAt = Instant.parse("2026-01-01T12:30:00Z"),
            outgoing = false,
            media = MediaInfo(kind = "video", sizeBytes = 1024, name = "clip.mp4", durationSeconds = 12.0),
        ),
        Message(
            id = 8,
            senderName = "Bob",
            text = "",
            sentAt = Instant.parse("2026-01-01T12:31:00Z"),
            outgoing = false,
            media = MediaInfo(kind = "document", sizeBytes = 2048, name = "notes.txt"),
        ),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the id, kind, size, name, duration and date`() {
        val rendered = render(OutputFormat.TABLE) { renderFiles(files) }

        val expected = listOf(
            "+----+----------+------+-----------+----------+----------------------+",
            "| id | kind     | size | name      | duration | date                 |",
            "+----+----------+------+-----------+----------+----------------------+",
            "| 7  | video    | 1024 | clip.mp4  | 0:12     | 2026-01-01T12:30:00Z |",
            "| 8  | document | 2048 | notes.txt |          | 2026-01-01T12:31:00Z |",
            "+----+----------+------+-----------+----------+----------------------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain files are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderFiles(files) }

        assertEquals(
            listOf(
                "id\tkind\tsize\tname\tduration\tdate",
                "7\tvideo\t1024\tclip.mp4\t0:12\t2026-01-01T12:30:00Z",
                "8\tdocument\t2048\tnotes.txt\t\t2026-01-01T12:31:00Z",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json files are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderFiles(files) }

        assertEquals(
            """[{"id":"7","kind":"video","size":"1024","name":"clip.mp4","duration":"0:12",""" +
                """"date":"2026-01-01T12:30:00Z"},""" +
                """{"id":"8","kind":"document","size":"2048","name":"notes.txt","duration":"",""" +
                """"date":"2026-01-01T12:31:00Z"}]""",
            rendered,
        )
    }

    @Test
    fun `a message with no media leaves the file columns empty`() {
        val rendered = render(OutputFormat.JSON) {
            renderFiles(
                listOf(
                    Message(
                        id = 9,
                        senderName = "Ada Lovelace",
                        text = "no media",
                        sentAt = Instant.parse("2026-01-01T12:32:00Z"),
                        outgoing = false,
                    ),
                ),
            )
        }

        assertEquals(
            """[{"id":"9","kind":"","size":"","name":"","duration":"","date":"2026-01-01T12:32:00Z"}]""",
            rendered,
        )
    }
}
