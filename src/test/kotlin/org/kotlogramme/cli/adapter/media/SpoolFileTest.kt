package org.kotlogramme.cli.adapter.media

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpoolFileTest {
    @Test
    fun `spooled bytes come back exactly as they went in`() {
        val bytes = byteArrayOf(0x00, 0x0D, 0x0A, 0x1A, 0xFF.toByte(), 0x42)

        val spool = SpoolFile.of(ByteArrayInputStream(bytes))

        assertEquals(bytes.size.toLong(), spool.size)
        spool.open().use { assertContentEquals(bytes, it.readBytes()) }
        spool.close()
    }

    @Test
    fun `an empty stream spools to an empty file`() {
        val spool = SpoolFile.of(ByteArrayInputStream(ByteArray(0)))

        assertEquals(0L, spool.size)
        spool.close()
    }

    @Test
    fun `a payload larger than one read buffer spools whole`() {
        val bytes = ByteArray(200_000) { index -> (index % 253).toByte() }

        val spool = SpoolFile.of(ByteArrayInputStream(bytes))

        assertEquals(bytes.size.toLong(), spool.size)
        spool.open().use { assertContentEquals(bytes, it.readBytes()) }
        spool.close()
    }

    @Test
    fun `closing removes the spool file`() {
        val spool = SpoolFile.of(ByteArrayInputStream("payload".toByteArray()))
        val before = spoolFiles()

        spool.close()

        assertEquals(before.size - 1, spoolFiles().size)
    }

    @Test
    fun `closing twice is harmless`() {
        val spool = SpoolFile.of(ByteArrayInputStream("payload".toByteArray()))
        val before = spoolFiles()

        spool.close()
        spool.close()

        assertEquals(before.size - 1, spoolFiles().size)
    }

    @Test
    fun `a close that cannot delete returns normally`() {
        val spool = SpoolFile.of(ByteArrayInputStream("payload".toByteArray()))
        spool.close()

        // The file is already gone, so the second close deletes nothing. It still has to return: a
        // cleanup failure must never mask the outcome of the upload that came first.
        spool.close()

        assertTrue(spoolFiles().none { it.fileName.toString().startsWith(SpoolFile.PREFIX) })
    }

    @Test
    fun `a stream that fails while copying does not leave a spool file behind`() {
        assertFailsWith<IOException> { SpoolFile.of(FailingStream()) }

        assertEquals(emptyList(), spoolFiles())
    }

    @Test
    fun `the spool file carries a recognisable prefix`() {
        val spool = SpoolFile.of(ByteArrayInputStream("payload".toByteArray()))

        assertTrue(spoolFiles().any { it.fileName.toString().startsWith(SpoolFile.PREFIX) })
        spool.close()
    }

    private fun spoolFiles(): List<Path> {
        val temp = Path.of(System.getProperty("java.io.tmpdir"))
        return Files.list(temp).use { files ->
            files.filter { it.fileName.toString().startsWith(SpoolFile.PREFIX) }.toList()
        }
    }
}

/** A stream that gives up halfway, the way a failing pipe does. */
private class FailingStream : InputStream() {
    private var served = 0

    override fun read(): Int {
        if (served > 0) throw IOException("the pipe broke")
        served++
        return 0x42
    }
}
