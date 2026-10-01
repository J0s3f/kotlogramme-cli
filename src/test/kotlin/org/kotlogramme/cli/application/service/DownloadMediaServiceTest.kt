package org.kotlogramme.cli.application.service

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.io.InputStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadMediaServiceTest {
    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `download passes the target through and returns the written path`() {
        val gateway = FakeDownloadGateway()
        val target = tempDir.resolve("out.bin")

        val written = DownloadMediaService(gateway).download("@ada", messageId = 12, target = target)

        assertEquals(DownloadCall("@ada", 12, target), gateway.downloads.single())
        assertEquals(target, written)
    }

    @Test
    fun `download accepts a bare file name beside the working directory`() {
        val gateway = FakeDownloadGateway()

        DownloadMediaService(gateway).download("@ada", messageId = 12, target = Path.of("out.bin"))

        assertEquals(Path.of("out.bin"), gateway.downloads.single().target)
    }

    @Test
    fun `rejects a non-positive message id before the gateway`() {
        val gateway = FakeDownloadGateway()

        assertFailsWith<IllegalArgumentException> {
            DownloadMediaService(gateway).download("@ada", messageId = 0, target = tempDir.resolve("out.bin"))
        }

        assertEquals(emptyList(), gateway.downloads)
    }

    @Test
    fun `rejects a target whose directory does not exist before the gateway`() {
        val gateway = FakeDownloadGateway()
        val target = tempDir.resolve("missing").resolve("out.bin")

        val error = assertFailsWith<IllegalArgumentException> {
            DownloadMediaService(gateway).download("@ada", messageId = 12, target = target)
        }

        assertTrue(error.message.orEmpty().contains("directory does not exist"))
        assertEquals(emptyList(), gateway.downloads)
    }

    @Test
    fun `fileName passes the reference and message id through`() {
        val gateway = FakeDownloadGateway()

        val name = DownloadMediaService(gateway).fileName("@ada", messageId = 12)

        assertEquals("cat.png", name)
    }

    @Test
    fun `fileName rejects a non-positive message id before the gateway`() {
        val gateway = FakeDownloadGateway()

        assertFailsWith<IllegalArgumentException> {
            DownloadMediaService(gateway).fileName("@ada", messageId = 0)
        }
    }
}

private data class DownloadCall(val reference: String, val messageId: Int, val target: Path)

private class FakeDownloadGateway : MediaGateway {
    val downloads = mutableListOf<DownloadCall>()

    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message = error("not used")

    override fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message = error("not used")

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message = error("not used")

    override fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message = error("not used")

    override fun copyMedia(
        reference: String,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message = error("not used")

    override fun download(reference: String, messageId: Int, target: Path): Path {
        downloads += DownloadCall(reference, messageId, target)
        return target
    }

    override fun fileName(reference: String, messageId: Int): String? = mediaName

    var mediaName: String? = "cat.png"
}
