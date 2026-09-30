package org.kotlogramme.cli.adapter.media

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.application.port.spi.MediaKindHint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileMediaProbeTest {
    @field:TempDir
    private lateinit var tempDir: Path

    private val probe = FileMediaProbe()

    @Test
    fun `maps the extension to a kind, ignoring case`() {
        val photos = listOf("cat.jpg", "cat.JPEG", "cat.png")
        val videos = listOf("clip.mp4", "clip.M4V", "clip.mov", "clip.mkv", "clip.webm", "clip.avi")
        val documents = listOf("notes.txt", "archive.tar.gz", "README", "no-extension.")

        photos.forEach { assertEquals(MediaKindHint.PHOTO, probe.probe(emptyFile(it)).kind, it) }
        videos.forEach { assertEquals(MediaKindHint.VIDEO, probe.probe(emptyFile(it)).kind, it) }
        documents.forEach { assertEquals(MediaKindHint.DOCUMENT, probe.probe(emptyFile(it)).kind, it) }
    }

    @Test
    fun `reads duration and dimensions from an ISO base media video`() {
        val file = Files.write(tempDir.resolve("clip.mp4"), isoVideoBytes())

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertEquals(2610.0, result.durationSeconds)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
    }

    @Test
    fun `reads metadata from every ISO base media extension`() {
        val names = listOf("clip.mp4", "clip.m4v", "clip.mov")
        names.forEach { name ->
            val file = Files.write(
                tempDir.resolve(name),
                isoVideoBytes(timescale = 90_000, duration = 9_000_000L, width = 1280, height = 720),
            )

            val result = probe.probe(file)

            assertEquals(100.0, result.durationSeconds, name)
            assertEquals(1280, result.width, name)
            assertEquals(720, result.height, name)
        }
    }

    @Test
    fun `falls back to the kind with null metadata for a truncated file`() {
        val truncated = isoVideoBytes().copyOf(24)
        val file = Files.write(tempDir.resolve("broken.mp4"), truncated)

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertNull(result.durationSeconds)
        assertNull(result.width)
        assertNull(result.height)
    }

    @Test
    fun `falls back to the kind with null metadata when the file is missing`() {
        val result = probe.probe(tempDir.resolve("gone.mp4"))

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertNull(result.durationSeconds)
    }

    @Test
    fun `tolerates a box that declares an enormous size`() {
        val bytes = isoBoxWithDeclaredSize("mdat", 0xFFFF_FFFFL, ByteArray(16)) + isoVideoBytes()
        val file = Files.write(tempDir.resolve("huge.mp4"), bytes)

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertNull(result.durationSeconds)
        assertNull(result.width)
        assertNull(result.height)
    }

    @Test
    fun `leaves metadata null for a container this build does not parse`() {
        val names = listOf("clip.avi")
        names.forEach { name ->
            val file = Files.write(tempDir.resolve(name), isoVideoBytes())

            val result = probe.probe(file)

            assertEquals(MediaKindHint.VIDEO, result.kind, name)
            assertNull(result.durationSeconds, name)
            assertNull(result.width, name)
            assertNull(result.height, name)
        }
    }

    @Test
    fun `reads duration and dimensions from a Matroska video`() {
        val file = Files.write(tempDir.resolve("clip.mkv"), mkvFileBytes())

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertEquals(5.0, result.durationSeconds)
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
    }

    @Test
    fun `applies a non-default timecode scale to the Matroska duration`() {
        val file = Files.write(
            tempDir.resolve("clip.webm"),
            mkvFileBytes(timecodeScale = 100_000L, durationTicks = 5_000.0, width = 640, height = 360),
        )

        val result = probe.probe(file)

        assertEquals(0.5, result.durationSeconds)
        assertEquals(640, result.width)
        assertEquals(360, result.height)
    }

    @Test
    fun `reads a single precision Matroska duration`() {
        val file = Files.write(
            tempDir.resolve("clip.mkv"),
            mkvFileBytes(durationTicks = 2_000.0, singlePrecisionDuration = true),
        )

        val result = probe.probe(file)

        assertEquals(2.0, result.durationSeconds)
    }

    @Test
    fun `reads only the duration when the Matroska file has no tracks`() {
        val file = Files.write(tempDir.resolve("clip.mkv"), mkvFileBytes(includeTracks = false))

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertEquals(5.0, result.durationSeconds)
        assertNull(result.width)
        assertNull(result.height)
    }

    @Test
    fun `ignores an audio-only Matroska track for dimensions`() {
        val file = Files.write(tempDir.resolve("clip.webm"), mkvFileBytes(includeVideoTrack = false))

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertEquals(5.0, result.durationSeconds)
        assertNull(result.width)
        assertNull(result.height)
    }

    @Test
    fun `skips Matroska clusters and still reads later metadata`() {
        val file = Files.write(
            tempDir.resolve("clip.mkv"),
            mkvFileBytes(clusterFirst = true, clusterBytes = 4096),
        )

        val result = probe.probe(file)

        assertEquals(5.0, result.durationSeconds)
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
    }

    @Test
    fun `falls back to the kind with null metadata for a truncated Matroska file`() {
        val file = Files.write(tempDir.resolve("broken.mkv"), mkvFileBytes().copyOf(16))

        val result = probe.probe(file)

        assertEquals(MediaKindHint.VIDEO, result.kind)
        assertNull(result.durationSeconds)
        assertNull(result.width)
        assertNull(result.height)
    }

    private fun emptyFile(name: String): Path = Files.write(tempDir.resolve(name), ByteArray(0))
}
