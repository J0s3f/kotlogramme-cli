package org.kotlogramme.cli.adapter.cli

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.media.isoVideoBytes
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
        assertEquals(listOf(SendFileCall("@ada", file, "a cat", true, null, false)), media.fileSends)
    }

    @Test
    fun `send-file sends a local file as a streamable video with the metadata`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val result = fixture.run(
            "send-file",
            "@ada",
            file.toString(),
            "--caption",
            "a clip",
            "--video",
            "--duration",
            "12.5",
            "--width",
            "1920",
            "--height",
            "1080",
        )

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "a clip", 12.5, 1920, 1080, null, false)), media.videoSends)
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file rejects --video together with --photo`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val result = fixture.run("send-file", "@ada", file.toString(), "--video", "--photo")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("mutually exclusive"), "stderr was: ${result.stderr}")
        assertTrue(media.videoSends.isEmpty())
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file rejects video metadata without the video flag`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val result = fixture.run("send-file", "@ada", file.toString(), "--duration", "12.5")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("require --video"), "stderr was: ${result.stderr}")
        assertTrue(media.videoSends.isEmpty())
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file streams stdin even with the video flag`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", "--video", stdin = "raw bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "raw bytes", "", false, null, false)), media.streamSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file reads stdin into a stream named stdin`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", stdin = "raw bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "raw bytes", "", false, null, false)), media.streamSends)
    }

    @Test
    fun `send-file names a stdin upload with the name option`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", "--name", "cat.png", stdin = "bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "cat.png", "bytes", "", false, null, false)), media.streamSends)
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
        assertEquals(
            listOf(SendUrlCall("@ada", "https://example.com/cat.png", "a cat", true, null, false)),
            media.urlSends,
        )
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
        assertEquals(listOf(CopyMediaCall("@ada", 12, "a cat", null, false)), media.copies)
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

    @Test
    fun `send-file detects a video file and sends it with the probed metadata`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--detect")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", 2610.0, 1920, 1080, null, false)), media.videoSends)
    }

    @Test
    fun `send-file detects a photo and sends it as a photo`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(0))

        val result = fixture.run("send-file", "@ada", file.toString(), "--detect")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", true, null, false)), media.fileSends)
    }

    @Test
    fun `send-file detects an unknown file and sends it as a document`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("notes.txt"), "hello".toByteArray())

        val result = fixture.run("send-file", "@ada", file.toString(), "--detect")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", false, null, false)), media.fileSends)
    }

    @Test
    fun `send-file lets explicit metadata override the probe`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--detect", "--duration", "5")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", 5.0, 1920, 1080, null, false)), media.videoSends)
    }

    @Test
    fun `send-file rejects detect together with an explicit kind`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val video = fixture.run("send-file", "@ada", file.toString(), "--detect", "--video")
        val photo = fixture.run("send-file", "@ada", file.toString(), "--detect", "--photo")

        assertEquals(1, video.statusCode)
        assertTrue(video.stderr.contains("cannot be combined"), "stderr was: ${video.stderr}")
        assertEquals(1, photo.statusCode)
        assertTrue(photo.stderr.contains("cannot be combined"), "stderr was: ${photo.stderr}")
        assertTrue(media.videoSends.isEmpty())
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file uses the name extension for the kind when detecting on stdin`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val photo = fixture.run("send-file", "@ada", "-", "--detect", "--name", "cat.png", stdin = "bytes")
        val video = fixture.run("send-file", "@ada", "-", "--detect", "--name", "clip.mp4", stdin = "bytes")

        assertEquals(0, photo.statusCode)
        assertEquals(0, video.statusCode)
        assertEquals(
            listOf(
                SendStreamCall("@ada", "cat.png", "bytes", "", true, null, false),
                SendStreamCall("@ada", "clip.mp4", "bytes", "", false, null, false),
            ),
            media.streamSends,
        )
    }

    @Test
    fun `send-file forwards the reply-to and silent flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("cat.png"))

        val result = fixture.run(
            "send-file",
            "@ada",
            file.toString(),
            "--caption",
            "a cat",
            "--photo",
            "--reply-to",
            "5",
            "--silent",
        )

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "a cat", true, 5, true)), media.fileSends)
    }

    @Test
    fun `send-file streams stdin with the reply-to and silent flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("send-file", "@ada", "-", "--reply-to", "5", "--silent", stdin = "bytes")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "bytes", "", false, 5, true)), media.streamSends)
    }

    @Test
    fun `send-file sends a video with the reply-to and silent flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.createFile(tempDir.resolve("clip.mp4"))

        val result = fixture.run("send-file", "@ada", file.toString(), "--video", "--reply-to", "5", "--silent")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", null, null, null, 5, true)), media.videoSends)
    }

    @Test
    fun `send-media-url forwards the reply-to and silent flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run(
            "send-media-url",
            "@ada",
            "https://example.com/cat.png",
            "--reply-to",
            "5",
            "--silent",
        )

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendUrlCall("@ada", "https://example.com/cat.png", "", false, 5, true)), media.urlSends)
    }

    @Test
    fun `copy-media forwards the reply-to and silent flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)

        val result = fixture.run("copy-media", "@ada", "12", "--caption", "a cat", "--reply-to", "5", "--silent")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(CopyMediaCall("@ada", 12, "a cat", 5, true)), media.copies)
    }
}
