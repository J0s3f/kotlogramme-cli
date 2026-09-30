package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DownloadedMedia
import com.github.badoualy.telegram.api.Media
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
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

        val sent = gatewayWith(operations).sendFile("@ada", file, caption = "a cat", asPhoto = true)

        assertEquals(SendFileCall(ada, file, "a cat", true), operations.fileSends.single())
        assertEquals(42, sent.id)
        assertEquals("photo", sent.mediaKind)
    }

    @Test
    fun `sendUrl resolves the reference and forwards the url and options`() {
        val operations = FakeMediaOperations().apply {
            sentUrl = message(id = 43, date = 1_000)
        }

        val sent = gatewayWith(operations).sendUrl("@ada", "https://example.com/cat.png", "a cat", false)

        assertEquals(SendUrlCall(ada, "https://example.com/cat.png", "a cat", false), operations.urlSends.single())
        assertEquals(43, sent.id)
    }

    @Test
    fun `copyMedia resolves the reference and forwards the source message id`() {
        val operations = FakeMediaOperations().apply {
            copied = message(id = 44, text = "caption", date = 1_000)
        }

        val sent = gatewayWith(operations).copyMedia("@ada", fromMessageId = 12, caption = "caption")

        assertEquals(CopyMediaCall(ada, 12, "caption"), operations.copies.single())
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

internal data class SendFileCall(val peer: TelegramPeer, val path: Path, val caption: String, val asPhoto: Boolean)

internal data class SendUrlCall(val peer: TelegramPeer, val url: String, val caption: String, val asPhoto: Boolean)

internal data class CopyMediaCall(val peer: TelegramPeer, val messageId: Int, val caption: String)

internal data class DownloadCall(val peer: TelegramPeer, val messageId: Int, val target: Path)

internal class FakeMediaOperations : FacadeMediaOperations {
    var sentFile: Message = message(id = 1)
    var sentUrl: Message = message(id = 1)
    var copied: Message = message(id = 1)
    var downloadedPath: String = "download.bin"

    val fileSends = mutableListOf<SendFileCall>()
    val urlSends = mutableListOf<SendUrlCall>()
    val copies = mutableListOf<CopyMediaCall>()
    val downloads = mutableListOf<DownloadCall>()

    override fun sendFile(peer: TelegramPeer, path: Path, caption: String, asPhoto: Boolean): Message {
        fileSends += SendFileCall(peer, path, caption, asPhoto)
        return sentFile
    }

    override fun sendUrl(peer: TelegramPeer, url: String, caption: String, asPhoto: Boolean): Message {
        urlSends += SendUrlCall(peer, url, caption, asPhoto)
        return sentUrl
    }

    override fun copyMedia(peer: TelegramPeer, fromMessageId: Int, caption: String): Message {
        copies += CopyMediaCall(peer, fromMessageId, caption)
        return copied
    }

    override fun download(peer: TelegramPeer, messageId: Int, target: Path): DownloadedMedia {
        downloads += DownloadCall(peer, messageId, target)
        return DownloadedMedia(downloadedPath, size = 10)
    }
}
