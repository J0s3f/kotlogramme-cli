package org.kotlogramme.cli.adapter.mcp.tools

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.cli.FakeDownloadMedia
import org.kotlogramme.cli.adapter.cli.FakeSendMedia
import org.kotlogramme.cli.adapter.mcp.files.FilesSandbox
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.domain.AlbumItem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileToolsTest {
    @TempDir
    lateinit var directory: Path

    private val sendMedia = FakeSendMedia()
    private val downloadMedia = FakeDownloadMedia(mediaName = "cat.png")
    private val telegram = fakeTelegramTools(sendMedia = sendMedia, downloadMedia = downloadMedia)

    private fun sandbox() = FilesSandbox.on(directory)

    private fun fileTool(name: String) = telegram.all(sandbox()).first { it.name == name }

    private fun write(relative: String, text: String = "data"): Path {
        val file = directory.resolve(relative)
        Files.createDirectories(file.parent)
        return Files.writeString(file, text)
    }

    // --- offering

    @Test
    fun `without a files directory there are no file tools`() {
        val names = telegram.all().map { it.name }

        assertTrue(names.none { it in FILE_TOOLS }, names.toString())
    }

    @Test
    fun `with a files directory the three file tools appear`() {
        val names = telegram.all(sandbox()).map { it.name }

        assertTrue(names.containsAll(FILE_TOOLS), names.toString())
    }

    @Test
    fun `sending needs read-write but saving into the directory does not`() {
        assertEquals(AccessLevel.READ_WRITE, fileTool("telegram_send_file").access)
        assertEquals(AccessLevel.READ_WRITE, fileTool("telegram_send_album").access)
        assertEquals(AccessLevel.READ_ONLY, fileTool("telegram_download_media").access)
    }

    @Test
    fun `saving a file says that it is not read only`() {
        val annotations = fileTool("telegram_download_media").annotations

        assertTrue(!annotations.readOnly && !annotations.destructive)
    }

    // --- sending

    @Test
    fun `send_file sends the real path of a file inside the directory`() {
        write("docs/report.pdf")

        val body = fileTool("telegram_send_file")
            .callWith("chat" to "@ada", "file" to "docs/report.pdf", "caption" to "see", "as_photo" to true)
            .body()

        val sent = sendMedia.fileSends.single()
        assertEquals(directory.toRealPath().resolve("docs").resolve("report.pdf"), sent.path)
        assertEquals(Triple("@ada", "see", true), Triple(sent.reference, sent.caption, sent.asPhoto))
        assertEquals("hello", body.items().single().text("text"))
    }

    @Test
    fun `send_file refuses a path outside the directory and sends nothing`() {
        val secret = Files.writeString(directory.resolveSibling("secret-${directory.fileName}.txt"), "secret")
        val tool = fileTool("telegram_send_file")

        listOf("../${secret.fileName}", secret.toString(), "missing.txt").forEach { file ->
            assertFailsWith<IllegalArgumentException>(file) { tool.callWith("chat" to "@ada", "file" to file) }
        }

        assertEquals(emptyList(), sendMedia.fileSends)
        Files.delete(secret)
    }

    @Test
    fun `send_album sends up to ten files in order with the caption on the first`() {
        write("1.jpg")
        write("2.jpg")

        fileTool("telegram_send_album")
            .callWith("chat" to "@ada", "files" to listOf("1.jpg", "2.jpg"), "caption" to "trip", "as_photo" to true)

        val real = directory.toRealPath()
        assertEquals(
            listOf(AlbumItem(real.resolve("1.jpg"), "trip", true), AlbumItem(real.resolve("2.jpg"), "", true)),
            sendMedia.albums.single().items,
        )
    }

    @Test
    fun `send_album refuses an empty list eleven files and one file outside`() {
        write("1.jpg")
        val tool = fileTool("telegram_send_album")

        assertFailsWith<IllegalArgumentException> { tool.callWith("chat" to "@ada", "files" to emptyList<String>()) }
        assertFailsWith<IllegalArgumentException> {
            tool.callWith("chat" to "@ada", "files" to (1..11).map { "$it.jpg" })
        }
        assertFailsWith<IllegalArgumentException> {
            tool.callWith("chat" to "@ada", "files" to listOf("1.jpg", "../x.jpg"))
        }
        assertEquals(emptyList(), sendMedia.albums)
    }

    // --- downloading

    @Test
    fun `download_media saves under the media's own name and reports the relative file`() {
        val body = fileTool("telegram_download_media").callWith("chat" to "@ada", "message_id" to 12).body()

        assertEquals(directory.toRealPath().resolve("cat.png"), downloadMedia.downloads.single().target)
        assertEquals("cat.png", body.text("file"))
        assertEquals("64", body.text("size"))
    }

    @Test
    fun `download_media saves under the name the model chose`() {
        val body = fileTool("telegram_download_media")
            .callWith("chat" to "@ada", "message_id" to 12, "file_name" to "mine.png").body()

        assertEquals("mine.png", body.text("file"))
    }

    @Test
    fun `download_media never replaces an existing file`() {
        write("cat.png", "precious")

        assertFailsWith<IllegalArgumentException> {
            fileTool("telegram_download_media").callWith("chat" to "@ada", "message_id" to 12)
        }

        assertEquals("precious", Files.readString(directory.resolve("cat.png")))
        assertEquals(emptyList(), downloadMedia.downloads)
    }

    @Test
    fun `download_media refuses a file name that leaves the directory`() {
        val tool = fileTool("telegram_download_media")

        listOf("../out.png", "sub/out.png", "C:/out.png", "..").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) {
                tool.callWith("chat" to "@ada", "message_id" to 12, "file_name" to name)
            }
        }
        assertEquals(emptyList(), downloadMedia.downloads)
    }

    @Test
    fun `a hostile media name from the sender is cleaned and cannot leave the directory`() {
        val hostile = FakeDownloadMedia(mediaName = "../../etc/pass:wd.txt. ")
        val tool = fakeTelegramTools(downloadMedia = hostile).all(sandbox()).first { it.name == "telegram_download_media" }

        val body = tool.callWith("chat" to "@ada", "message_id" to 3).body()

        assertEquals("pass_wd.txt", body.text("file"))
        assertTrue(hostile.downloads.single().target.startsWith(directory.toRealPath()))
    }

    @Test
    fun `a media name nothing can be made of falls back to the message id`() {
        val nameless = FakeDownloadMedia(mediaName = "..")
        val tool = fakeTelegramTools(downloadMedia = nameless).all(sandbox()).first { it.name == "telegram_download_media" }

        assertEquals("media-3", tool.callWith("chat" to "@ada", "message_id" to 3).body().text("file"))
    }

    @Test
    fun `a media with no name at all falls back to the message id`() {
        val nameless = FakeDownloadMedia(mediaName = null)
        val tool = fakeTelegramTools(downloadMedia = nameless).all(sandbox()).first { it.name == "telegram_download_media" }

        assertEquals("media-8", tool.callWith("chat" to "@ada", "message_id" to 8).body().text("file"))
    }

    private companion object {
        val FILE_TOOLS = listOf("telegram_send_file", "telegram_send_album", "telegram_download_media")
    }
}
