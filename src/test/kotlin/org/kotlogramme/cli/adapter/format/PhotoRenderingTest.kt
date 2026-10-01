package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Photo
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoRenderingTest {
    private val photos = listOf(
        Photo(id = 42, dcId = 2, sizeBytes = 1024, width = 320, height = 240, spoiler = false),
        Photo(id = 43, dcId = null, sizeBytes = 2048, width = null, height = null, spoiler = true),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `plain photos are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderPhotos(photos) }

        assertEquals(
            listOf(
                "id\tdc\tsize\twidth\theight\tspoiler",
                "42\t2\t1024\t320\t240\t",
                "43\t\t2048\t\t\tyes",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json photos are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderPhotos(listOf(photos.first())) }

        assertEquals(
            """[{"id":"42","dc":"2","size":"1024","width":"320","height":"240","spoiler":""}]""",
            rendered,
        )
    }
}
