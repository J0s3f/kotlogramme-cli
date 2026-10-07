package org.kotlogramme.cli.application.port.spi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadProgressTest {
    @Test
    fun `a declared total gives a fraction and a whole percentage`() {
        val progress = UploadProgress(bytesSent = 250, totalBytes = 1000, elapsedMillis = 1000)

        assertTrue(progress.hasTotal)
        assertEquals(0.25, progress.fraction)
        assertEquals(25, progress.percent)
    }

    @Test
    fun `a percentage rounds down`() {
        assertEquals(33, UploadProgress(bytesSent = 1, totalBytes = 3, elapsedMillis = 1).percent)
    }

    @Test
    fun `no declared total means no fraction and no percentage`() {
        val progress = UploadProgress(bytesSent = 250, totalBytes = 0, elapsedMillis = 1000)

        assertFalse(progress.hasTotal)
        assertNull(progress.fraction)
        assertNull(progress.percent)
    }

    @Test
    fun `more bytes than the total never goes past one hundred percent`() {
        val progress = UploadProgress(bytesSent = 2000, totalBytes = 1000, elapsedMillis = 1000)

        assertEquals(1.0, progress.fraction)
        assertEquals(100, progress.percent)
    }

    @Test
    fun `the rate is the bytes over the elapsed time`() {
        assertEquals(500.0, UploadProgress(bytesSent = 1000, totalBytes = 5000, elapsedMillis = 2000).bytesPerSecond)
    }

    @Test
    fun `the rate is zero before any time has passed`() {
        assertEquals(0.0, UploadProgress(bytesSent = 1000, totalBytes = 5000, elapsedMillis = 0).bytesPerSecond)
    }
}
