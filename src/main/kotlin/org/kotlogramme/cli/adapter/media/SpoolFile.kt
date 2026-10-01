package org.kotlogramme.cli.adapter.media

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * A byte stream copied to a temporary file, so an upload can declare its size without holding it.
 *
 * A pipe carries no length, and Telegram has to be told the total before the first part is sent, so
 * the bytes are spooled to disk once. That buys three things a buffered array cannot: the size is
 * exact, the upload still goes out in bounded chunks and is never resident in the heap, and the
 * copy tolerates bytes that are not text at all — a piped photo or archive survives the round trip
 * unchanged.
 *
 * Closing deletes the spool file, which is what the caller does once the upload ends, so the file
 * goes on the success and the failure path alike. A deletion that fails must not mask the outcome of
 * the upload that came first, so it is swallowed rather than thrown.
 */
class SpoolFile private constructor(private val file: Path, val size: Long) : Closeable {
    /** A fresh stream over the spooled copy, for an upload to read in chunks. */
    fun open(): InputStream = Files.newInputStream(file)

    override fun close() {
        runCatching { Files.deleteIfExists(file) }
    }

    companion object {
        /** The name every spool file starts with, so a leftover one is recognisable. */
        const val PREFIX = "kotlogramme-stdin-"

        /** Copies [source] to end-of-input into a new temporary file and reports its size. */
        fun of(source: InputStream): SpoolFile {
            val file = Files.createTempFile(PREFIX, SUFFIX)
            try {
                // copyTo moves the bytes through a fixed buffer, so an input of any size costs the same.
                Files.newOutputStream(file).use { out -> source.copyTo(out) }
            } catch (failure: IOException) {
                // A copy that broke halfway has no value, so the partial file goes rather than lying
                // in the temp directory until the machine restarts.
                runCatching { Files.deleteIfExists(file) }
                throw failure
            }
            return SpoolFile(file, Files.size(file))
        }

        private const val SUFFIX = ".upload"
    }
}
