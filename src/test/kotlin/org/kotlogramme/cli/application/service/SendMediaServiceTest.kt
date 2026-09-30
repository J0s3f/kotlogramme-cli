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

        val sent = SendMediaService(gateway).sendFile("@ada", file, caption = "a cat", asPhoto = true, null, false)

        assertEquals(SendFileCall("@ada", file, "a cat", true, null, false), gateway.fileSends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `sendFile accepts an empty caption`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("cat.png"))

        SendMediaService(gateway).sendFile("@ada", file, caption = "", asPhoto = false, null, false)

        assertEquals(SendFileCall("@ada", file, "", false, null, false), gateway.fileSends.single())
    }

    @Test
    fun `sendVideo passes a real file and the video metadata through unchanged`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val sent = SendMediaService(gateway).sendVideo(
            "@ada",
            file,
            caption = "a clip",
            durationSeconds = 12.5,
            width = 1920,
            height = 1080,
            replyToMessageId = null,
            silent = false,
        )

        assertEquals(SendVideoCall("@ada", file, "a clip", 12.5, 1920, 1080, null, false), gateway.videoSends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `sendVideo accepts absent metadata`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        SendMediaService(gateway).sendVideo("@ada", file, caption = "", null, null, null, null, false)

        assertEquals(SendVideoCall("@ada", file, "", null, null, null, null, false), gateway.videoSends.single())
    }

    @Test
    fun `sendVideo rejects a missing file before the gateway`() {
        val gateway = FakeMediaGateway()
        val missing = tempDir.resolve("nope.mp4")

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendVideo("@ada", missing, "", null, null, null, null, false)
        }

        assertTrue(error.message.orEmpty().contains("does not exist"))
        assertEquals(emptyList(), gateway.videoSends)
    }

    @Test
    fun `sendVideo rejects a directory before the gateway`() {
        val gateway = FakeMediaGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendVideo("@ada", tempDir, "", null, null, null, null, false)
        }

        assertTrue(error.message.orEmpty().contains("not a regular file"))
        assertEquals(emptyList(), gateway.videoSends)
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
            replyToMessageId = null,
            silent = false,
        )

        assertEquals(
            SendStreamCall("@ada", "cat.png", "cat", "a cat", true, null, false),
            gateway.streamSends.single(),
        )
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `sendStream accepts an empty stream and an empty caption`() {
        val gateway = FakeMediaGateway()

        SendMediaService(gateway).sendStream("@ada", "empty.bin", ByteArrayInputStream(ByteArray(0)), "", false, null, false)

        assertEquals(
            SendStreamCall("@ada", "empty.bin", "", "", false, null, false),
            gateway.streamSends.single(),
        )
    }

    @Test
    fun `rejects a blank name before the gateway`() {
        val gateway = FakeMediaGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendStream("@ada", "  ", ByteArrayInputStream(ByteArray(0)), "", false, null, false)
        }

        assertTrue(error.message.orEmpty().contains("name"))
        assertEquals(emptyList(), gateway.streamSends)
    }

    @Test
    fun `sendUrl passes the url and options through unchanged`() {
        val gateway = FakeMediaGateway()

        val sent = SendMediaService(gateway).sendUrl("@ada", "https://example.com/cat.png", "a cat", true, null, false)

        assertEquals(
            SendUrlCall("@ada", "https://example.com/cat.png", "a cat", true, null, false),
            gateway.urlSends.single(),
        )
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `copyMedia passes the source message id and caption through unchanged`() {
        val gateway = FakeMediaGateway()

        val sent = SendMediaService(gateway).copyMedia("@ada", fromMessageId = 12, caption = "a cat", null, false)

        assertEquals(CopyMediaCall("@ada", 12, "a cat", null, false), gateway.copies.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `rejects a missing file before the gateway`() {
        val gateway = FakeMediaGateway()
        val missing = tempDir.resolve("nope.png")

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendFile("@ada", missing, "", asPhoto = false, null, false)
        }

        assertTrue(error.message.orEmpty().contains("does not exist"))
        assertEquals(emptyList(), gateway.fileSends)
    }

    @Test
    fun `rejects a directory before the gateway`() {
        val gateway = FakeMediaGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendFile("@ada", tempDir, "", asPhoto = false, null, false)
        }

        assertTrue(error.message.orEmpty().contains("not a regular file"))
        assertEquals(emptyList(), gateway.fileSends)
    }

    @Test
    fun `rejects a non-positive source message id before the gateway`() {
        val gateway = FakeMediaGateway()

        assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).copyMedia("@ada", fromMessageId = 0, caption = "", null, false)
        }

        assertEquals(emptyList(), gateway.copies)
    }

    @Test
    fun `sendFile passes the reply-to and silent flags through`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("cat.png"))

        SendMediaService(gateway).sendFile("@ada", file, "", asPhoto = false, replyToMessageId = 5, silent = true)

        assertEquals(SendFileCall("@ada", file, "", false, 5, true), gateway.fileSends.single())
    }

    @Test
    fun `sendVideo passes the reply-to and silent flags through`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        SendMediaService(gateway).sendVideo("@ada", file, "", null, null, null, replyToMessageId = 5, silent = true)

        assertEquals(SendVideoCall("@ada", file, "", null, null, null, 5, true), gateway.videoSends.single())
    }

    @Test
    fun `sendStream passes the reply-to and silent flags through`() {
        val gateway = FakeMediaGateway()

        SendMediaService(gateway).sendStream(
            "@ada",
            "cat.png",
            ByteArrayInputStream("cat".toByteArray()),
            "",
            false,
            replyToMessageId = 5,
            silent = true,
        )

        assertEquals(SendStreamCall("@ada", "cat.png", "cat", "", false, 5, true), gateway.streamSends.single())
    }

    @Test
    fun `sendUrl passes the reply-to and silent flags through`() {
        val gateway = FakeMediaGateway()

        SendMediaService(gateway).sendUrl("@ada", "https://example.com/cat.png", "", false, 5, true)

        assertEquals(
            SendUrlCall("@ada", "https://example.com/cat.png", "", false, 5, true),
            gateway.urlSends.single(),
        )
    }

    @Test
    fun `copyMedia passes the reply-to and silent flags through`() {
        val gateway = FakeMediaGateway()

        SendMediaService(gateway).copyMedia("@ada", 12, "", replyToMessageId = 5, silent = true)

        assertEquals(CopyMediaCall("@ada", 12, "", 5, true), gateway.copies.single())
    }

    @Test
    fun `rejects a non-positive reply-to message id before the gateway`() {
        val gateway = FakeMediaGateway()
        val file = Files.createFile(tempDir.resolve("cat.png"))

        assertFailsWith<IllegalArgumentException> {
            SendMediaService(gateway).sendFile("@ada", file, "", asPhoto = false, replyToMessageId = 0, silent = false)
        }

        assertEquals(emptyList(), gateway.fileSends)
    }
}

private data class SendFileCall(
    val reference: String,
    val path: Path,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private data class SendVideoCall(
    val reference: String,
    val path: Path,
    val caption: String,
    val durationSeconds: Double?,
    val width: Int?,
    val height: Int?,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private data class SendStreamCall(
    val reference: String,
    val name: String,
    val content: String,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private data class SendUrlCall(
    val reference: String,
    val url: String,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private data class CopyMediaCall(
    val reference: String,
    val fromMessageId: Int,
    val caption: String,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private class FakeMediaGateway : MediaGateway {
    val fileSends = mutableListOf<SendFileCall>()
    val videoSends = mutableListOf<SendVideoCall>()
    val urlSends = mutableListOf<SendUrlCall>()
    val copies = mutableListOf<CopyMediaCall>()
    val streamSends = mutableListOf<SendStreamCall>()

    var sent: Message = message
    var downloaded: Path = Path.of("download.bin")

    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        fileSends += SendFileCall(reference, path, caption, asPhoto, replyToMessageId, silent)
        return sent
    }

    override fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        videoSends += SendVideoCall(
            reference,
            path,
            caption,
            durationSeconds,
            width,
            height,
            replyToMessageId,
            silent,
        )
        return sent
    }

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        streamSends += SendStreamCall(
            reference,
            name,
            data.readBytes().decodeToString(),
            caption,
            asPhoto,
            replyToMessageId,
            silent,
        )
        return sent
    }

    override fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        urlSends += SendUrlCall(reference, url, caption, asPhoto, replyToMessageId, silent)
        return sent
    }

    override fun copyMedia(
        reference: String,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        copies += CopyMediaCall(reference, fromMessageId, caption, replyToMessageId, silent)
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
