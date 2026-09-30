package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.Folder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderRenderingTest {
    private val work = Chat(
        id = 7,
        title = "Work",
        kind = ChatKind.GROUP,
        username = null,
        lastMessagePreview = null,
        lastMessageAt = null,
    )

    private val folders = listOf(
        Folder(id = 0, title = "All", kind = "default"),
        Folder(id = 1, title = "Work", kind = "filter", pinnedPeers = listOf(work), excludePeers = listOf(work)),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the three peer counts`() {
        val rendered = render(OutputFormat.TABLE) { renderFolders(folders) }

        val expected = listOf(
            "+----+---------+-------+--------+----------+----------+",
            "| id | kind    | title | pinned | included | excluded |",
            "+----+---------+-------+--------+----------+----------+",
            "| 0  | default | All   | 0      | 0        | 0        |",
            "| 1  | filter  | Work  | 1      | 0        | 1        |",
            "+----+---------+-------+--------+----------+----------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain folders are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderFolders(folders) }

        assertEquals(
            listOf(
                "id\tkind\ttitle\tpinned\tincluded\texcluded",
                "0\tdefault\tAll\t0\t0\t0",
                "1\tfilter\tWork\t1\t0\t1",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json folders are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderFolders(folders) }

        assertEquals(
            """[{"id":"0","kind":"default","title":"All","pinned":"0","included":"0","excluded":"0"},""" +
                """{"id":"1","kind":"filter","title":"Work","pinned":"1","included":"0","excluded":"1"}]""",
            rendered,
        )
    }
}
