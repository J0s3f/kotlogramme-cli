package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DownloadedMedia
import com.github.badoualy.telegram.api.Media
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import com.github.badoualy.telegram.api.UploadedFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramMediaGatewayTest {
    private val ada = peer(id = 7, kind = "user", username = "ada", name = "Ada")

    @Test
    fun `sendFile resolves the reference and forwards the upload options`() {
        val operations = FakeMediaOperations().apply {
            sentFile = message(id = 42, text = "", date = 1_000, media = Media("photo"))
        }
        val file = Path.of("media", "cat.png")

        val sent = gatewayWith(operations).sendFile(
            "@ada",
            file,
            caption = "a cat",
            asPhoto = true,
            replyToMessageId = 5,
            silent = true,
        )

        assertEquals(SendFileCall(ada, file, "a cat", true, 5, true), operations.fileSends.single())
        assertEquals(42, sent.id)
        assertEquals("photo", sent.media?.kind)
    }

    @Test
    fun `sendVideo resolves the reference and forwards the video metadata`() {
        val operations = FakeMediaOperations().apply {
            sentVideo = message(id = 46, text = "a clip", date = 1_000, media = Media("video"))
        }
        val file = Path.of("media", "clip.mp4")

        val sent = gatewayWith(operations).sendVideo(
            "@ada",
            file,
            caption = "a clip",
            durationSeconds = 12.5,
            width = 1920,
            height = 1080,
            replyToMessageId = 5,
            silent = true,
        )

        assertEquals(SendVideoCall(ada, file, "a clip", 12.5, 1920, 1080, 5, true), operations.videoSends.single())
        assertEquals(46, sent.id)
        assertEquals("video", sent.media?.kind)
    }

    @Test
    fun `sendStream uploads the stream and then sends the uploaded file by handle`() {
        val operations = FakeMediaOperations().apply {
            uploaded = UploadedFile(id = 5, name = "cat.png", size = 3, parts = 1, handle = 99)
            sentUploaded = message(id = 45, text = "a cat", date = 1_000, media = Media("photo"))
        }
        val data = ByteArrayInputStream("cat".toByteArray())

        val sent = gatewayWith(operations).sendStream(
            "@ada",
            "cat.png",
            data,
            caption = "a cat",
            asPhoto = true,
            replyToMessageId = 5,
            silent = true,
        )

        assertEquals(
            listOf(
                UploadStreamCall("cat.png", "cat"),
                SendUploadedCall(ada, operations.uploaded, "a cat", true, 5, true),
            ),
            operations.calls,
        )
        assertEquals(45, sent.id)
        assertEquals("photo", sent.media?.kind)
    }

    @Test
    fun `sendUrl resolves the reference and forwards the url and options`() {
        val operations = FakeMediaOperations().apply {
            sentUrl = message(id = 43, date = 1_000)
        }

        val sent = gatewayWith(operations).sendUrl("@ada", "https://example.com/cat.png", "a cat", false, 5, true)

        assertEquals(
            SendUrlCall(ada, "https://example.com/cat.png", "a cat", false, 5, true),
            operations.urlSends.single(),
        )
        assertEquals(43, sent.id)
    }

    @Test
    fun `copyMedia resolves the reference and forwards the source message id`() {
        val operations = FakeMediaOperations().apply {
            copied = message(id = 44, text = "caption", date = 1_000)
        }

        val sent = gatewayWith(operations).copyMedia(
            "@ada",
            fromMessageId = 12,
            caption = "caption",
            replyToMessageId = 5,
            silent = true,
        )

        assertEquals(CopyMediaCall(ada, 12, "caption", 5, true), operations.copies.single())
        assertEquals(44, sent.id)
        assertEquals("caption", sent.text)
    }

    @Test
    fun `download resolves the reference and maps the written path`() {
        val operations = FakeMediaOperations().apply { downloadedPath = "media/out.bin" }
        val target = Path.of("media", "out.bin")

        val written = gatewayWith(operations).download("@ada", messageId = 12, target = target)

        assertEquals(DownloadCall(ada, 12, target), operations.downloads.single())
        assertEquals(Path.of("media/out.bin"), written)
    }

    private fun gatewayWith(operations: FakeMediaOperations): KotlogramMediaGateway =
        KotlogramMediaGateway(
            operations,
            ChatReferenceResolver(FakeChatOperations().apply { resolvedPeer = ada }),
        )
}

internal data class SendFileCall(
    val peer: TelegramPeer,
    val path: Path,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class SendVideoCall(
    val peer: TelegramPeer,
    val path: Path,
    val caption: String,
    val durationSeconds: Double?,
    val width: Int?,
    val height: Int?,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class UploadStreamCall(val name: String, val content: String)

internal data class SendUploadedCall(
    val peer: TelegramPeer,
    val file: UploadedFile,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class SendUrlCall(
    val peer: TelegramPeer,
    val url: String,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class CopyMediaCall(
    val peer: TelegramPeer,
    val messageId: Int,
    val caption: String,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class DownloadCall(val peer: TelegramPeer, val messageId: Int, val target: Path)

internal class FakeMediaOperations : FacadeMediaOperations {
    var sentFile: Message = message(id = 1)
    var sentVideo: Message = message(id = 1)
    var sentUrl: Message = message(id = 1)
    var copied: Message = message(id = 1)
    var downloadedPath: String = "download.bin"
    var uploaded: UploadedFile = UploadedFile(id = 1, name = "upload.bin", size = 0, parts = 0, handle = 1)
    var sentUploaded: Message = message(id = 1)

    val fileSends = mutableListOf<SendFileCall>()
    val videoSends = mutableListOf<SendVideoCall>()
    val urlSends = mutableListOf<SendUrlCall>()
    val copies = mutableListOf<CopyMediaCall>()
    val downloads = mutableListOf<DownloadCall>()
    val calls = mutableListOf<Any>()

    override fun sendFile(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        fileSends += SendFileCall(peer, path, caption, asPhoto, replyToMessageId, silent)
        return sentFile
    }

    override fun sendVideo(
        peer: TelegramPeer,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        videoSends += SendVideoCall(peer, path, caption, durationSeconds, width, height, replyToMessageId, silent)
        return sentVideo
    }

    override fun uploadStream(data: InputStream, name: String): UploadedFile {
        calls += UploadStreamCall(name, data.readBytes().decodeToString())
        return uploaded
    }

    override fun sendUploaded(
        peer: TelegramPeer,
        file: UploadedFile,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        calls += SendUploadedCall(peer, file, caption, asPhoto, replyToMessageId, silent)
        return sentUploaded
    }

    override fun sendUrl(
        peer: TelegramPeer,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        urlSends += SendUrlCall(peer, url, caption, asPhoto, replyToMessageId, silent)
        return sentUrl
    }

    override fun copyMedia(
        peer: TelegramPeer,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        copies += CopyMediaCall(peer, fromMessageId, caption, replyToMessageId, silent)
        return copied
    }

    override fun download(peer: TelegramPeer, messageId: Int, target: Path): DownloadedMedia {
        downloads += DownloadCall(peer, messageId, target)
        return DownloadedMedia(downloadedPath, size = 10)
    }
}
