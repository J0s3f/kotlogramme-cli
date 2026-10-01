package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.UploadProgress
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadProgressBarTest {
    @Test
    fun `the bar is on by default only where a person is watching`() {
        assertTrue(progressEnabled(progress = false, noProgress = false, terminal = true))
        assertFalse(progressEnabled(progress = false, noProgress = false, terminal = false))
    }

    @Test
    fun `either flag overrides the terminal test`() {
        assertTrue(progressEnabled(progress = true, noProgress = false, terminal = false))
        assertFalse(progressEnabled(progress = false, noProgress = true, terminal = true))
    }

    @Test
    fun `no-progress wins when both flags are given`() {
        assertFalse(progressEnabled(progress = true, noProgress = true, terminal = true))
    }

    @Test
    fun `a declared total renders a percentage of the bytes sent`() {
        val line = progressLine(UploadProgress(bytesSent = 512, totalBytes = 1_024, elapsedMillis = 1_000), 1_024, color = false)

        assertTrue(line.contains("50%"), "expected a percentage in $line")
        assertTrue(line.contains("512 B/1.0 KB"), "expected both totals in $line")
    }

    @Test
    fun `an unknown total renders a count and a rate but no percentage`() {
        val line = progressLine(UploadProgress(bytesSent = 1_024, totalBytes = 0, elapsedMillis = 2_000), 0, color = false)

        assertFalse(line.contains("%"), "expected no percentage in $line")
        assertTrue(line.contains("1.0 KB"), "expected the bytes sent in $line")
        assertTrue(line.contains("/s"), "expected a rate in $line")
        assertTrue(line.contains("0:02"), "expected the elapsed time in $line")
    }

    @Test
    fun `without colour the line is plain ASCII`() {
        val line = progressLine(UploadProgress(bytesSent = 256, totalBytes = 1_024, elapsedMillis = 1_000), 1_024, color = false)

        assertTrue(line.all { it.code < 128 }, "expected ASCII only in $line")
    }

    @Test
    fun `with colour the line carries escapes that do not count towards its length`() {
        val line = progressLine(UploadProgress(bytesSent = 1_024, totalBytes = 1_024, elapsedMillis = 1_000), 1_024, color = true)

        assertTrue(line.contains("["), "expected an escape in $line")
        assertTrue(line.contains("100%"), "expected a percentage in $line")
        // The escapes are zero-width on screen, so the eraser has to pad from what a person sees
        // rather than from the raw characters.
        assertTrue(visibleLength(line) < line.length, "expected escapes in $line")
    }

    @Test
    fun `an upload that finishes before the first tick writes nothing at all`() {
        val out = RecordingStream()
        val bar = UploadProgressBar(out, color = false, intervalMillis = 10_000)

        val slot = bar.begin(1_024)
        slot.follow { UploadProgress(bytesSent = 1_024, totalBytes = 1_024, elapsedMillis = 5) }
        slot.close()

        assertEquals("", out.text())
    }

    @Test
    fun `a watched upload paints a line and erases it on close`() {
        val out = RecordingStream()
        val bar = UploadProgressBar(out, color = false, intervalMillis = 1)

        val slot = bar.begin(1_024)
        slot.follow { UploadProgress(bytesSent = 512, totalBytes = 1_024, elapsedMillis = 1_000) }
        Thread.sleep(WAIT_MILLIS)
        slot.close()

        val painted = out.text()
        assertTrue(painted.contains("50%"), "expected a painted line in '$painted'")
        assertTrue(painted.endsWith("\r"), "expected the line to be erased in place in '$painted'")
        assertTrue(out.text().substringAfterLast("\r").isBlank(), "expected the erase to leave a clean line")
    }

    @Test
    fun `a bar whose upload never reports leaves nothing behind`() {
        val out = RecordingStream()
        val bar = UploadProgressBar(out, color = false, intervalMillis = 1)

        val slot = bar.begin(1_024)
        Thread.sleep(WAIT_MILLIS)
        slot.close()

        assertEquals("", out.text())
    }

    @Test
    fun `closing twice is harmless`() {
        val out = RecordingStream()
        val bar = UploadProgressBar(out, color = false, intervalMillis = 10_000)
        val slot = bar.begin(1_024)

        slot.close()
        slot.close()

        assertEquals("", out.text())
    }

    private companion object {
        const val WAIT_MILLIS = 60L
    }
}

/** A stream that keeps what was written to it, so a test can read the bar's own bytes back. */
private class RecordingStream : ByteArrayOutputStream() {
    fun text(): String = toString(Charsets.UTF_8)
}

/** [RecordingStream] wrapped the way a bar takes it. */
private fun UploadProgressBar(out: RecordingStream, color: Boolean, intervalMillis: Long) =
    UploadProgressBar(PrintStream(out, true, Charsets.UTF_8), color, intervalMillis)
