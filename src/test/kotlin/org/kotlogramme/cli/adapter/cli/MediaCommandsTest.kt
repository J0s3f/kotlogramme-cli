package org.kotlogramme.cli.adapter.cli

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCommandsTest {
    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `send-file sends a local file with the caption and the photo flag`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("cat.png"))

        val result = fixture.run("send-file", "@ada", file.toString(), "--caption", "a cat", "--photo")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "a cat", true)), media.fileSends)
    }

    @Test
    fun `send-file reads stdin into a stream named stdin`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", stdin = "raw bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "raw bytes", "", false)), media.streamSends)
    }

    @Test
    fun `send-file names a stdin upload with the name option`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", "--name", "cat.png", stdin = "bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "cat.png", "bytes", "", false)), media.streamSends)
    }

    @Test
    fun `send-file reports a missing file as a usage error`() {
        val media = FakeSendMedia(rejection = IllegalArgumentException("file does not exist: nope.png"))
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "nope.png")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("does not exist"), "stderr was: ${result.stderr}")
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-media-url sends the url with the caption and the photo flag`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run(
            "send-media-url",
            "@ada",
            "https://example.com/cat.png",
            "--caption",
            "a cat",
            "--photo",
        )

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendUrlCall("@ada", "https://example.com/cat.png", "a cat", true)), media.urlSends)
    }

    @Test
    fun `send-media-url reports a blank url as a usage error`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-media-url", "@ada", "   ")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("blank"), "stderr was: ${result.stderr}")
        assertTrue(media.urlSends.isEmpty())
    }

    @Test
    fun `copy-media sends the source message id with the caption`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("copy-media", "@ada", "12", "--caption", "a cat")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(CopyMediaCall("@ada", 12, "a cat")), media.copies)
    }

    @Test
    fun `copy-media reports a non-positive message id as a usage error`() {
        val media = FakeSendMedia(rejection = IllegalArgumentException("message id must be positive but was 0"))
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("copy-media", "@ada", "0")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("must be positive"), "stderr was: ${result.stderr}")
        assertTrue(media.copies.isEmpty())
    }
}
