package org.kotlogramme.cli.adapter.cli

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.format.UploadProgressBar
import org.kotlogramme.cli.adapter.media.SpoolFile
import org.kotlogramme.cli.adapter.media.isoVideoBytes
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.domain.AlbumItem
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("raw bytes"))

        val result = fixture.run("send-file", "@ada", "-", "--video")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "raw bytes", "", false, null, false)), media.streamSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file reads stdin into a stream named stdin`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("raw bytes"))

        val result = fixture.run("send-file", "@ada", "-")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendStreamCall("@ada", "stdin", "raw bytes", "", false, null, false)), media.streamSends)
    }

    @Test
    fun `send-file names a stdin upload with the name option`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("bytes"))

        val result = fixture.run("send-file", "@ada", "-", "--name", "cat.png")

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
        // One fixture per run, because a pipe is drained by the first read: a second run on the same
        // stream would see nothing, which is what really happens to a process's stdin as well.
        val photo = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("bytes"))
            .run("send-file", "@ada", "-", "--detect", "--name", "cat.png")
        val video = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("bytes"))
            .run("send-file", "@ada", "-", "--detect", "--name", "clip.mp4")

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
    fun `send-file sends an mp4 as a video by default with the probed metadata`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", 2610.0, 1920, 1080, null, false)), media.videoSends)
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file sends a png as a photo by default`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(0))

        val result = fixture.run("send-file", "@ada", file.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", true, null, false)), media.fileSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file sends an unknown file as a document by default`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("notes.txt"), "hello".toByteArray())

        val result = fixture.run("send-file", "@ada", file.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", false, null, false)), media.fileSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file sends a file with no extension as a document by default`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("README"), "hello".toByteArray())

        val result = fixture.run("send-file", "@ada", file.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", false, null, false)), media.fileSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file sends a video whose metadata cannot be read as a video by default`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.avi"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString())

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", null, null, null, null, false)), media.videoSends)
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file --no-detect forces a plain document for a video file`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--no-detect")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", false, null, false)), media.fileSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file --photo wins over detection for a video file`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--photo")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendFileCall("@ada", file, "", true, null, false)), media.fileSends)
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file --video wins over detection for a photo file`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(0))

        val result = fixture.run("send-file", "@ada", file.toString(), "--video")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendVideoCall("@ada", file, "", null, null, null, null, false)), media.videoSends)
        assertTrue(media.fileSends.isEmpty())
    }

    @Test
    fun `send-file rejects --no-detect together with --detect`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--no-detect", "--detect")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("mutually exclusive"), "stderr was: ${result.stderr}")
        assertTrue(media.fileSends.isEmpty())
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file shows no bar by default when the output is not a terminal`() {
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = false, progressFactory = { watcher })
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(64))

        fixture.run("send-file", "@ada", file.toString())

        assertEquals(listOf(UploadProgressReporter.SILENT), media.progressReports)
        assertEquals(emptyList(), watcher.totals)
    }

    @Test
    fun `send-file shows a bar by default when the output is a terminal`() {
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = true, progressFactory = { watcher })
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(4_096))

        fixture.run("send-file", "@ada", file.toString())

        // The file's own length is the total, known before the first byte moves, so a bar has a real
        // percentage to draw rather than a made-up one.
        assertEquals(listOf(4_096L), watcher.totals)
    }

    @Test
    fun `--progress forces a bar where the output is not a terminal`() {
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = false, progressFactory = { watcher })
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(4_096))

        fixture.run("send-file", "@ada", file.toString(), "--progress")

        assertEquals(listOf(4_096L), watcher.totals)
    }

    @Test
    fun `--no-progress turns the bar off on a terminal`() {
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = true, progressFactory = { watcher })
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(64))

        fixture.run("send-file", "@ada", file.toString(), "--no-progress")

        assertEquals(listOf(UploadProgressReporter.SILENT), media.progressReports)
        assertEquals(emptyList(), watcher.totals)
    }

    @Test
    fun `--no-progress wins when both progress flags are given`() {
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = true, progressFactory = { watcher })
        val file = Files.write(tempDir.resolve("cat.png"), ByteArray(64))

        val result = fixture.run("send-file", "@ada", file.toString(), "--progress", "--no-progress")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(UploadProgressReporter.SILENT), media.progressReports)
    }

    @Test
    fun `send-file declares the spooled length as the bar's total for a pipe`() {
        val piped = ByteArray(2_048) { index -> (index % 251).toByte() }
        val media = FakeSendMedia()
        val watcher = SilentWatcher()
        val fixture = cliFixture(
            sendMedia = media,
            sendFileCommand = sendFileWithBytes(piped),
            interactiveTerminal = true,
            progressFactory = { watcher },
        )

        fixture.run("send-file", "@ada", "-")

        // Spooling is what gives a pipe a real total, so a piped upload gets a percentage too.
        assertEquals(listOf(2_048L), watcher.totals)
    }
    @Test
    fun `a failed upload leaves nothing painted behind`() {
        val out = ByteArrayOutputStream()
        val media = FakeSendMedia(rejection = IllegalArgumentException("telegram refused the upload"))
        val fixture = cliFixture(
            sendMedia = media,
            sendFileCommand = sendFileWithText("payload"),
            interactiveTerminal = true,
            progressFactory = { UploadProgressBar(PrintStream(out, true, Charsets.UTF_8)) },
        )

        fixture.run("send-file", "@ada", "-")

        // A failure must leave the terminal as clean as a success: no half-drawn line, no stray CR.
        val painted = out.toString(Charsets.UTF_8)
        assertTrue(painted.substringAfterLast("\r").isBlank(), "expected a clean line, got '$painted'")
    }

    @Test
    fun `the progress flags do not disturb the kind flags`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, interactiveTerminal = false)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val photo = fixture.run("send-file", "@ada", file.toString(), "--photo", "--progress")
        val detect = fixture.run("send-file", "@ada", file.toString(), "--detect", "--progress")
        val noDetect = fixture.run("send-file", "@ada", file.toString(), "--no-detect", "--progress")
        val video = fixture.run("send-file", "@ada", file.toString(), "--video", "--progress", "--duration", "4")

        assertEquals(0, photo.statusCode)
        assertEquals(0, detect.statusCode)
        assertEquals(0, noDetect.statusCode)
        assertEquals(0, video.statusCode)
        // --photo forces a photo, --no-detect forces a plain document, and --detect on the .mp4 and --video
        // each go out as a video: adding --progress to any of them changes none of that.
        assertEquals(listOf(true, false), media.fileSends.map { it.asPhoto })
        assertEquals(2, media.videoSends.size)
    }

    @Test
    fun `send-file rejects --no-detect together with an explicit kind`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val video = fixture.run("send-file", "@ada", file.toString(), "--no-detect", "--video")
        val photo = fixture.run("send-file", "@ada", file.toString(), "--no-detect", "--photo")

        assertEquals(1, video.statusCode)
        assertTrue(video.stderr.contains("cannot be combined"), "stderr was: ${video.stderr}")
        assertEquals(1, photo.statusCode)
        assertTrue(photo.stderr.contains("cannot be combined"), "stderr was: ${photo.stderr}")
        assertTrue(media.fileSends.isEmpty())
        assertTrue(media.videoSends.isEmpty())
    }

    @Test
    fun `send-file rejects metadata together with --no-detect`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media)
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = fixture.run("send-file", "@ada", file.toString(), "--no-detect", "--duration", "5")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("require --video"), "stderr was: ${result.stderr}")
        assertTrue(media.fileSends.isEmpty())
        assertTrue(media.videoSends.isEmpty())
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
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("bytes"))

        val result = fixture.run("send-file", "@ada", "-", "--reply-to", "5", "--silent")

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

    @Test
    fun `send-file uploads the piped bytes byte for byte`() {
        // The bytes a pipe can carry that no text decoder survives: a NUL, a bare CR, an LF, two
        // bytes that are not valid UTF-8 and a lone 0x1A. Reading stdin as text would turn these
        // into replacement characters and a different length, which is a corrupted upload.
        val piped = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0xFF.toByte(), 0xFE.toByte(),
            0x00, 0x42,
        )
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithBytes(piped))

        val result = fixture.run("send-file", "@ada", "-", "--name", "photo.png")

        assertEquals(0, result.statusCode)
        assertEquals(1, media.streamSends.size)
        assertContentEquals(piped, media.streamSends.single().bytes)
        assertEquals(piped.size.toLong(), media.streamSends.single().size)
    }

    @Test
    fun `send-file uploads a payload larger than one chunk intact`() {
        // Larger than the facade's 512 KiB chunk, so a reader that truncated or stopped at a chunk
        // boundary would show it: the whole payload has to arrive.
        val piped = ByteArray(1_500_000) { index -> (index % 251).toByte() }
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithBytes(piped))

        val result = fixture.run("send-file", "@ada", "-")

        assertEquals(0, result.statusCode)
        assertContentEquals(piped, media.streamSends.single().bytes)
    }

    @Test
    fun `send-file deletes the spool file after a successful upload`() {
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("payload"))

        fixture.run("send-file", "@ada", "-")

        assertEquals(emptyList(), spoolFiles())
    }

    @Test
    fun `send-file deletes the spool file after a failed upload`() {
        val media = FakeSendMedia(rejection = IllegalArgumentException("telegram refused the upload"))
        val fixture = cliFixture(sendMedia = media, sendFileCommand = sendFileWithText("payload"))

        fixture.run("send-file", "@ada", "-")

        assertEquals(emptyList(), spoolFiles())
    }

    @Test
    fun `send-file measures a stdin upload itself rather than asking the pipe`() {
        // An InputStream that refuses to say how long it is: the command has to spool the bytes and
        // measure the copy, because the upload needs a total and the pipe does not carry one.
        val bytes = ByteArray(4_096) { index -> (index % 97).toByte() }
        val media = FakeSendMedia()
        val fixture = cliFixture(sendMedia = media, sendFileCommand = SendFileCommand(LenLessStream(bytes)))

        val result = fixture.run("send-file", "@ada", "-")

        assertEquals(0, result.statusCode)
        assertEquals(bytes.size.toLong(), media.streamSends.single().size)
        assertContentEquals(bytes, media.streamSends.single().bytes)
    }

    private fun spoolFiles(): List<Path> {
        val temp = Path.of(System.getProperty("java.io.tmpdir"))
        return Files.list(temp).use { files ->
            files.filter { it.fileName.toString().startsWith(SpoolFile.PREFIX) }.toList()
        }
    }

    @Test
    fun `send-album sends every file and captions only the first`() {
        val media = FakeSendMedia()
        val first = Files.createFile(tempDir.resolve("a.png"))
        val second = Files.createFile(tempDir.resolve("b.png"))

        val result = cliFixture(sendMedia = media)
            .run("send-album", "@ada", first.toString(), second.toString(), "--caption", "trip", "--photo")

        assertEquals(0, result.statusCode)
        assertEquals(
            SendAlbumCall("@ada", listOf(AlbumItem(first, "trip", true), AlbumItem(second, "", true))),
            media.albums.single(),
        )
    }

    @Test
    fun `send-album sends a file that is not an image as a document`() {
        val media = FakeSendMedia()
        val notes = Files.writeString(tempDir.resolve("notes.txt"), "just text")

        cliFixture(sendMedia = media).run("send-album", "@ada", notes.toString())

        assertEquals(listOf(AlbumItem(notes, "", false)), media.albums.single().items)
    }

    @Test
    fun `send-album needs at least one file`() {
        val result = cliFixture().run("send-album", "@ada")

        assertNotEquals(0, result.statusCode)
    }

    @Test
    fun `send-album reports a missing file as a usage error`() {
        val media = FakeSendMedia(rejection = IllegalArgumentException("file does not exist"))

        val result = cliFixture(sendMedia = media).run("send-album", "@ada", tempDir.resolve("gone.png").toString(), "--photo")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("file does not exist"), result.stderr)
    }
}

/** A stream that will not report its length, the way a pipe genuinely cannot. */
private class LenLessStream(private val bytes: ByteArray) : InputStream() {
    private var position = 0

    override fun read(): Int =
        if (position >= bytes.size) -1 else bytes[position++].toInt() and 0xFF

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val count = minOf(length, bytes.size - position)
        if (count == 0) return -1
        bytes.copyInto(target, offset, position, position + count)
        position += count
        return count
    }

    override fun available(): Int = throw UnsupportedOperationException("a pipe has no length")
}
