package org.kotlogramme.cli.adapter.media

import org.kotlogramme.cli.application.port.spi.MediaKindHint
import org.kotlogramme.cli.application.port.spi.MediaProbe
import org.kotlogramme.cli.application.port.spi.MediaProbeResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * A [MediaProbe] that works out the kind from the file name and, for an ISO base media video, reads
 * the duration and dimensions from the container itself.
 *
 * Nothing here shells out to ffmpeg or adds a dependency. The ISO walk reads only box headers and
 * the two small boxes that hold the metadata, so the `mdat` of a multi-gigabyte recording is seeked
 * over, never read. Matroska (`.mkv`, `.webm`) and AVI (`.avi`) are recognised as videos but keep
 * their null metadata: this build does not parse those containers, and that is a supported outcome,
 * not an error. A malformed or truncated file falls back to the same kind with null metadata.
 */
class FileMediaProbe : MediaProbe {
    override fun probe(path: Path): MediaProbeResult {
        val fileName = path.fileName.toString()
        val kind = mediaKindOf(fileName)
        if (kind != MediaKindHint.VIDEO || !isIsoBaseMedia(fileName)) return MediaProbeResult(kind)
        val metadata = readIsoMetadata(path)
        return MediaProbeResult(kind, metadata?.durationSeconds, metadata?.width, metadata?.height)
    }

    private fun readIsoMetadata(path: Path): VideoMetadata? =
        try {
            FileChannel.open(path, StandardOpenOption.READ).use(::parseMovieBox)
        } catch (_: Exception) {
            null
        }

    private fun parseMovieBox(channel: FileChannel): VideoMetadata? {
        val movie = findBox(channel, FILE_START, channel.size(), MOOV) ?: return null
        var durationSeconds: Double? = null
        var width: Int? = null
        var height: Int? = null
        var position = movie.payloadStart
        while (position + BASE_BOX_HEADER_SIZE <= movie.payloadEnd) {
            val box = readBox(channel, position, movie.payloadEnd) ?: break
            when (box.type) {
                MVHD -> if (durationSeconds == null) durationSeconds = readDurationSeconds(channel, box)
                TRAK -> if (width == null) {
                    val dimensions = readTrackDimensions(channel, box)
                    width = dimensions?.first
                    height = dimensions?.second
                }
            }
            position = box.payloadEnd
        }
        if (durationSeconds == null && width == null && height == null) return null
        return VideoMetadata(durationSeconds, width, height)
    }

    private fun readDurationSeconds(channel: FileChannel, mvhd: Box): Double? {
        val payload = readPayload(channel, mvhd, MVHD_PAYLOAD_LIMIT) ?: return null
        val version = payload.get(0).toInt()
        val timescale: Long
        val duration: Long
        if (version == 1) {
            if (payload.limit() < MVHD_V1_LENGTH) return null
            timescale = payload.getInt(MVHD_V1_TIMESCALE).toLong() and UNSIGNED_INT
            duration = payload.getLong(MVHD_V1_DURATION)
        } else {
            if (payload.limit() < MVHD_V0_LENGTH) return null
            timescale = payload.getInt(MVHD_V0_TIMESCALE).toLong() and UNSIGNED_INT
            duration = payload.getInt(MVHD_V0_DURATION).toLong() and UNSIGNED_INT
        }
        if (timescale <= 0L || duration <= 0L || duration == UNKNOWN_DURATION_32) return null
        return duration.toDouble() / timescale
    }

    private fun readTrackDimensions(channel: FileChannel, track: Box): Pair<Int, Int>? {
        val tkhd = findBox(channel, track.payloadStart, track.payloadEnd, TKHD) ?: return null
        val payload = readPayload(channel, tkhd, TKHD_PAYLOAD_LIMIT) ?: return null
        val version = payload.get(0).toInt()
        val widthOffset = if (version == 1) TKHD_WIDTH_OFFSET_V1 else TKHD_WIDTH_OFFSET_V0
        if (payload.limit() < widthOffset + 8) return null
        val width = payload.getInt(widthOffset) / FIXED_POINT_ONE
        val height = payload.getInt(widthOffset + 4) / FIXED_POINT_ONE
        if (width <= 0 || height <= 0) return null
        return width to height
    }

    private fun findBox(channel: FileChannel, start: Long, end: Long, type: String): Box? {
        var position = start
        while (position + BASE_BOX_HEADER_SIZE <= end) {
            val box = readBox(channel, position, end) ?: return null
            if (box.type == type) return box
            position = box.payloadEnd
        }
        return null
    }

    /**
     * Reads one box header at [position]. A box whose declared size runs past [limit] is clamped to
     * it rather than rejected, so a truncated file still yields whatever its leading boxes hold. The
     * header is at most sixteen bytes; the payload is never read here.
     */
    private fun readBox(channel: FileChannel, position: Long, limit: Long): Box? {
        val declared = readBoxHeader(channel, position, limit) ?: return null
        val payloadStart = position + declared.headerSize
        val payloadEnd =
            if (declared.size == 0L || declared.size > limit - position) limit else position + declared.size
        if (payloadEnd < payloadStart) return null
        return Box(declared.type, payloadStart, payloadEnd)
    }

    private fun readBoxHeader(channel: FileChannel, position: Long, limit: Long): BoxHeader? {
        if (position + BASE_BOX_HEADER_SIZE > limit) return null
        val header = ByteBuffer.allocate(EXTENDED_BOX_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        channel.position(position)
        while (header.hasRemaining()) {
            if (channel.read(header) <= 0) break
        }
        if (header.position() < BASE_BOX_HEADER_SIZE) return null
        header.flip()
        val size = header.getInt(0).toLong() and UNSIGNED_INT
        val type = String(header.array(), TYPE_OFFSET, TYPE_LENGTH, Charsets.US_ASCII)
        if (size != EXTENDED_SIZE_MARKER) return BoxHeader(type, size, BASE_BOX_HEADER_SIZE.toLong())
        if (header.limit() < EXTENDED_BOX_HEADER_SIZE) return null
        val extended = header.getLong(EXTENDED_SIZE_OFFSET)
        if (extended < EXTENDED_BOX_HEADER_SIZE) return null
        return BoxHeader(type, extended, EXTENDED_BOX_HEADER_SIZE.toLong())
    }

    private fun readPayload(channel: FileChannel, box: Box, maxBytes: Int): ByteBuffer? {
        val available = minOf(box.payloadEnd - box.payloadStart, maxBytes.toLong())
        if (available <= 0L) return null
        val payload = ByteBuffer.allocate(available.toInt()).order(ByteOrder.BIG_ENDIAN)
        channel.position(box.payloadStart)
        while (payload.hasRemaining()) {
            if (channel.read(payload) <= 0) break
        }
        payload.flip()
        return payload
    }

    private companion object {
        const val FILE_START = 0L
        const val BASE_BOX_HEADER_SIZE = 8
        const val EXTENDED_BOX_HEADER_SIZE = 16
        const val EXTENDED_SIZE_MARKER = 1L
        const val EXTENDED_SIZE_OFFSET = 8
        const val TYPE_OFFSET = 4
        const val TYPE_LENGTH = 4
        const val UNSIGNED_INT = 0xFFFFFFFFL
        const val FIXED_POINT_ONE = 65536
        const val UNKNOWN_DURATION_32 = 0xFFFFFFFFL
        const val MVHD_PAYLOAD_LIMIT = 32
        const val MVHD_V0_LENGTH = 20
        const val MVHD_V0_TIMESCALE = 12
        const val MVHD_V0_DURATION = 16
        const val MVHD_V1_LENGTH = 32
        const val MVHD_V1_TIMESCALE = 20
        const val MVHD_V1_DURATION = 24
        const val TKHD_PAYLOAD_LIMIT = 96
        const val TKHD_WIDTH_OFFSET_V0 = 76
        const val TKHD_WIDTH_OFFSET_V1 = 88
        const val MOOV = "moov"
        const val MVHD = "mvhd"
        const val TRAK = "trak"
        const val TKHD = "tkhd"
    }
}

/** What a local file should be sent as, decided by its name. */
internal fun mediaKindOf(fileName: String): MediaKindHint =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png" -> MediaKindHint.PHOTO
        "mp4", "m4v", "mov", "mkv", "webm", "avi" -> MediaKindHint.VIDEO
        else -> MediaKindHint.DOCUMENT
    }

private val ISO_EXTENSIONS = setOf("mp4", "m4v", "mov")

private fun isIsoBaseMedia(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in ISO_EXTENSIONS

private data class Box(val type: String, val payloadStart: Long, val payloadEnd: Long)

private data class BoxHeader(val type: String, val size: Long, val headerSize: Long)

private data class VideoMetadata(val durationSeconds: Double?, val width: Int?, val height: Int?)
