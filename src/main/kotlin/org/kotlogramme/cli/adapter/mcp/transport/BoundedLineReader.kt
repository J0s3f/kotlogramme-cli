package org.kotlogramme.cli.adapter.mcp.transport

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** One line read from the client. */
sealed interface Line {
    data class Text(val value: String) : Line

    /** A line longer than the limit; its bytes were discarded, so the next line starts clean. */
    data object TooLong : Line
}

/**
 * Reads UTF-8 lines from a byte stream without ever holding more than [maxBytes] of one line.
 *
 * `BufferedReader.readLine` grows without bound, so a client (or a bug) that never sends a line feed could
 * exhaust the heap. This reader discards the excess of an over long line, reports it once as [Line.TooLong]
 * and carries on with the next line. Decoding happens per complete line, so a character split across two
 * reads is never broken.
 */
class BoundedLineReader(
    private val input: InputStream,
    private val maxBytes: Int,
    chunkSize: Int = DEFAULT_CHUNK_BYTES,
) {
    private val chunk = ByteArray(chunkSize)
    private var position = 0
    private var filled = 0

    /** The next line, or `null` at the end of the stream. */
    fun next(): Line? {
        val line = ByteArrayOutputStream()
        var overflowed = false
        var started = false
        while (true) {
            if (position == filled && !refill()) {
                return if (started) finish(line, overflowed) else null
            }
            started = true
            val end = indexOfLineFeed()
            val take = (if (end >= 0) end else filled) - position
            if (line.size() + take > maxBytes) overflowed = true else line.write(chunk, position, take)
            if (end >= 0) {
                position = end + 1
                return finish(line, overflowed)
            }
            position = filled
        }
    }

    private fun finish(line: ByteArrayOutputStream, overflowed: Boolean): Line {
        if (overflowed) return Line.TooLong
        val bytes = line.toByteArray()
        val length = if (bytes.isNotEmpty() && bytes.last() == CARRIAGE_RETURN) bytes.size - 1 else bytes.size
        return Line.Text(String(bytes, 0, length, Charsets.UTF_8))
    }

    private fun indexOfLineFeed(): Int {
        for (index in position until filled) {
            if (chunk[index] == LINE_FEED) return index
        }
        return -1
    }

    private fun refill(): Boolean {
        val read = input.read(chunk)
        if (read <= 0) return false
        position = 0
        filled = read
        return true
    }

    companion object {
        const val DEFAULT_CHUNK_BYTES = 8192
        private const val LINE_FEED = '\n'.code.toByte()
        private const val CARRIAGE_RETURN = '\r'.code.toByte()
    }
}
