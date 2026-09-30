package org.kotlogramme.cli.application.service

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.domain.Message
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SendMediaServiceTest {
    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `sendFile passes a real file and the options through unchanged`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("cat.png"))

        val sent = SendMediaService(gateway).sendFile("@ada", file, caption = "a cat", asPhoto = true)

        assertEquals(SendFileCall("@ada", file, "a cat", true), gateway.fileSends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `sendFile accepts an empty caption`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("cat.png"))

        SendMediaService(gateway).sendFile("@ada", file, caption = "", asPhoto = false)

        assertEquals(SendFileCall("@ada", file, "", false), gateway.fileSends.single())
    }

    @Test
    fun `sendStream passes the named stream and options through unchanged`() {
        val gateway = FakeMediaGateway()

        val sent = SendMediaService(gateway).sendStream(
            "@ada",
            name = "cat.png",
            data = ByteArrayInputStream("cat".toByteArray()),
            caption = "a cat",
            asPhoto = true,
        )

        assertEquals(SendStreamCall("@ada", "cat.png", "cat", "a cat", true), gateway.streamSends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `sendStream accepts an empty stream and an empty caption`() {
        val gateway = FakeMediaGateway()

        SendMediaService(gateway).sendStream("@ada", "empty.bin", ByteArrayInputStream(ByteArray(0)), "", false)

        assertEquals(SendStreamCall("@ada", "empty.bin", "", "", false), gateway.streamSends.single())
    }

    @Test
    fun `rejects a blank name before the gateway`() {
        val gateway = FakeMediaGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendStream("@ada", "  ", ByteArrayInputStream(ByteArray(0)), "", false)
        }

        assertTrue(error.message.orEmpty().contains("name"))
        assertEquals(emptyList(), gateway.streamSends)
    }

    @Test
    fun `sendUrl passes the url and options through unchanged`() {
        val gateway = FakeMediaGateway()

        val sent = SendMediaService(gateway).sendUrl("@ada", "https://example.com/cat.png", "a cat", true)

        assertEquals(SendUrlCall("@ada", "https://example.com/cat.png", "a cat", true), gateway.urlSends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `copyMedia passes the source message id and caption through unchanged`() {
        val gateway = FakeMediaGateway()

        val sent = SendMediaService(gateway).copyMedia("@ada", fromMessageId = 12, caption = "a cat")

        assertEquals(CopyMediaCall("@ada", 12, "a cat"), gateway.copies.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `rejects a missing file before the gateway`() {
        val gateway = FakeMediaGateway()
        val missing = tempDir.resolve("nope.png")

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendFile("@ada", missing, "", asPhoto = false)
        }

        assertTrue(error.message.orEmpty().contains("does not exist"))
        assertEquals(emptyList(), gateway.fileSends)
    }

    @Test
    fun `rejects a directory before the gateway`() {
        val gateway = FakeMediaGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendFile("@ada", tempDir, "", asPhoto = false)
        }

        assertTrue(error.message.orEmpty().contains("not a regular file"))
        assertEquals(emptyList(), gateway.fileSends)
    }

    @Test
    fun `rejects a non-positive source message id before the gateway`() {
        val gateway = FakeMediaGateway()

        assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).copyMedia("@ada", fromMessageId = 0, caption = "")
        }

        assertEquals(emptyList(), gateway.copies)
    }
}

private data class SendFileCall(val reference: String, val path: Path, val caption: String, val asPhoto: Boolean)

private data class SendStreamCall(
    val reference: String,
    val name: String,
    val content: String,
    val caption: String,
    val asPhoto: Boolean,
)

private data class SendUrlCall(val reference: String, val url: String, val caption: String, val asPhoto: Boolean)

private data class CopyMediaCall(val reference: String, val fromMessageId: Int, val caption: String)

private class FakeMediaGateway : MediaGateway {
    val fileSends = mutableListOf<SendFileCall>()
    val urlSends = mutableListOf<SendUrlCall>()
    val copies = mutableListOf<CopyMediaCall>()
    val streamSends = mutableListOf<SendStreamCall>()

    var sent: Message = message
    var downloaded: Path = Path.of("download.bin")

    override fun sendFile(reference: String, path: Path, caption: String, asPhoto: Boolean): Message {
        fileSends += SendFileCall(reference, path, caption, asPhoto)
        return sent
    }

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
    ): Message {
        streamSends += SendStreamCall(reference, name, data.readBytes().decodeToString(), caption, asPhoto)
        return sent
    }

    override fun sendUrl(reference: String, url: String, caption: String, asPhoto: Boolean): Message {
        urlSends += SendUrlCall(reference, url, caption, asPhoto)
        return sent
    }

    override fun copyMedia(reference: String, fromMessageId: Int, caption: String): Message {
        copies += CopyMediaCall(reference, fromMessageId, caption)
        return sent
    }

    override fun download(reference: String, messageId: Int, target: Path): Path {
        this.downloaded = target
        return target
    }
}

private val message = Message(
    id = 1,
    senderName = "Ada",
    text = "hi",
    sentAt = Instant.parse("2026-09-30T10:00:00Z"),
    outgoing = true,
)
