package org.kotlogramme.cli.adapter.cli

import com.github.badoualy.telegram.api.MessageSearchFilter
import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.TelegramException
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileCommandsTest {
    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `download-media writes to the given path and reports the size`() {
        val media = FakeDownloadMedia()
        val fixture = cliFixture(downloadMedia = media)
        val target = tempDir.resolve("out.bin")

        val result = fixture.run("download-media", "@ada", "12", target.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(DownloadMediaCall("@ada", 12, target)), media.downloads)
        assertEquals(listOf("$target\t64"), fixture.output.lines)
        assertEquals(64, Files.size(target))
    }

    @Test
    fun `download-media creates missing parent directories`() {
        val media = FakeDownloadMedia()
        val fixture = cliFixture(downloadMedia = media)
        val target = tempDir.resolve("nested").resolve("dir").resolve("out.bin")

        val result = fixture.run("download-media", "@ada", "12", target.toString())

        assertEquals(0, result.statusCode)
        assertTrue(Files.exists(target))
        assertEquals(64, Files.size(target))
    }

    @Test
    fun `download-media overwrites an existing file`() {
        val media = FakeDownloadMedia()
        val fixture = cliFixture(downloadMedia = media)
        val target = Files.write(tempDir.resolve("out.bin"), ByteArray(5))

        val result = fixture.run("download-media", "@ada", "12", target.toString())

        assertEquals(0, result.statusCode)
        assertEquals(64, Files.size(target))
    }

    @Test
    fun `download-media derives the default file name from the media name`() {
        val media = FakeDownloadMedia(mediaName = "cat.png")
        val fixture = cliFixture(downloadMedia = media)

        val result = fixture.run("download-media", "@ada", "12")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(DownloadMediaCall("@ada", 12, Path.of("cat.png"))), media.downloads)
        assertEquals(listOf("cat.png\t64"), fixture.output.lines)
        Files.deleteIfExists(Path.of("cat.png"))
    }

    @Test
    fun `download-media falls back to the message id when the media has no name`() {
        val media = FakeDownloadMedia(mediaName = null)
        val fixture = cliFixture(downloadMedia = media)

        val result = fixture.run("download-media", "@ada", "12")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(DownloadMediaCall("@ada", 12, Path.of("12"))), media.downloads)
        assertEquals(listOf("12\t64"), fixture.output.lines)
        Files.deleteIfExists(Path.of("12"))
    }

    @Test
    fun `download-media reports a message with no media as a usage error`() {
        val media = FakeDownloadMedia(telegramRejection = TelegramException("message 12 has no media"))
        val fixture = cliFixture(downloadMedia = media)

        val result = fixture.run("download-media", "@ada", "12", tempDir.resolve("out.bin").toString())

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("no media"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), media.downloads)
    }

    @Test
    fun `download-media reports an unresolvable peer as a usage error`() {
        val media = FakeDownloadMedia(rejection = IllegalArgumentException("Cannot resolve 'nope'"))
        val fixture = cliFixture(downloadMedia = media)

        val result = fixture.run("download-media", "nope", "12", tempDir.resolve("out.bin").toString())

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("Cannot resolve 'nope'"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), media.downloads)
    }

    @Test
    fun `download-media reports an unresolvable message id as a usage error`() {
        val media = FakeDownloadMedia(telegramRejection = TelegramException("message 12 does not resolve"))
        val fixture = cliFixture(downloadMedia = media)

        val result = fixture.run("download-media", "@ada", "12", tempDir.resolve("out.bin").toString())

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("does not resolve"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), media.downloads)
    }

    @Test
    fun `list-files renders a table with the file columns`() {
        val search = FakeSearchMessages(
            fileResults = listOf(
                Message(
                    id = 7,
                    senderName = "Ada",
                    text = "",
                    sentAt = Instant.parse("2026-01-01T12:30:00Z"),
                    outgoing = false,
                    media = MediaInfo(kind = "video", sizeBytes = 1024, name = "clip.mp4", durationSeconds = 12.0),
                ),
            ),
        )
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("list-files", "@ada", "--kind", "video")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "id\tkind\tsize\tname\tduration\tdate",
                "7\tvideo\t1024\tclip.mp4\t0:12\t2026-01-01T12:30:00Z",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `list-files defaults to every kind and a limit of 20`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        fixture.run("list-files", "@ada")

        assertEquals(FileSearchCall("@ada", MediaFileKind.ALL, 20), search.fileSearches.single())
    }

    @Test
    fun `list-files maps every friendly kind name to its wire filter`() {
        val cases = mapOf(
            "photo" to "photos",
            "photos" to "photos",
            "video" to "video",
            "photo-video" to "photoVideo",
            "document" to "document",
            "file" to "document",
            "audio" to "music",
            "music" to "music",
            "voice" to "voice",
            "gif" to "gif",
            "animation" to "gif",
        )
        for ((name, wire) in cases) {
            val search = FakeSearchMessages()
            val fixture = cliFixture(searchMessages = search)

            fixture.run("list-files", "@ada", "--kind", name)

            assertEquals(wire, search.fileSearches.single().kind.filter, "kind '$name'")
        }
    }

    @Test
    fun `every kind's filter is the wire name of the facade's search filter`() {
        // ALL names no filter of its own; the service expands it before the gateway is asked.
        assertEquals("", MediaFileKind.ALL.filter)
        for (kind in MediaFileKind.entries.filterNot { it == MediaFileKind.ALL }) {
            val filter = MessageSearchFilter.entries.firstOrNull { it.wire == kind.filter }
            assertNotNull(filter, "kind '${kind.cliName}' names wire '${kind.filter}', which no filter carries")
        }
    }

    @Test
    fun `list-files rejects an unknown kind listing the valid names`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("list-files", "@ada", "--kind", "nope")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("unknown file kind 'nope'"), "stderr was: ${result.stderr}")
        assertTrue(
            result.stderr.contains("all, photo, video, photo-video, document, audio, voice, gif, animation"),
            "stderr was: ${result.stderr}",
        )
        assertEquals(emptyList(), search.fileSearches)
    }

    @Test
    fun `list-files passes the limit through`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        fixture.run("list-files", "@ada", "--kind", "gif", "--limit", "5")

        assertEquals(FileSearchCall("@ada", MediaFileKind.GIF, 5), search.fileSearches.single())
    }

    @Test
    fun `list-files --total prints just the count`() {
        val search = FakeSearchMessages(fileTotalResults = 42)
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("list-files", "@ada", "--kind", "document", "--total")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("42"), fixture.output.lines)
        assertEquals(listOf("@ada" to MediaFileKind.DOCUMENT), search.fileTotals)
        assertEquals(emptyList(), search.fileSearches)
    }

    @Test
    fun `list-files passes the cursor to the use case`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        fixture.run("list-files", "@ada", "--after", "99")

        assertEquals(listOf<String?>("99"), search.fileCursors)
    }

    @Test
    fun `list-files prints the next cursor on a full page`() {
        val messages = (1..20).map { index ->
            Message(
                id = index,
                senderName = "Ada",
                text = "",
                sentAt = Instant.parse("2026-01-01T12:30:00Z"),
                outgoing = false,
                media = MediaInfo(kind = "video", sizeBytes = 1024, name = "clip.mp4"),
            )
        }
        val search = FakeSearchMessages(fileResults = messages)
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("list-files", "@ada")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 20"), fixture.output.text)
    }

    @Test
    fun `list-files omits the next cursor on a short page`() {
        val search = FakeSearchMessages(
            fileResults = listOf(
                Message(
                    id = 7,
                    senderName = "Ada",
                    text = "",
                    sentAt = Instant.parse("2026-01-01T12:30:00Z"),
                    outgoing = false,
                    media = MediaInfo(kind = "video", sizeBytes = 1024, name = "clip.mp4"),
                ),
            ),
        )
        val fixture = cliFixture(searchMessages = search)

        fixture.run("list-files", "@ada")

        assertTrue(!fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `list-files rejects a malformed cursor`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("list-files", "@ada", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
    }
}
