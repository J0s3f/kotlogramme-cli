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
 * A [MediaProbe] that works out the kind from the file name and, for a video container it
 * understands, reads the duration and dimensions from the file itself.
 *
 * Nothing here shells out to ffmpeg or adds a dependency. The ISO walk reads only box headers and
 * the two small boxes that hold the metadata, so the `mdat` of a multi-gigabyte recording is seeked
 * over, never read. The Matroska (`.mkv`, `.webm`) walk does the same in EBML terms: element sizes
 * are read as headers and every element (including a `Cluster`) is skipped by its declared size, so
 * the media payload itself is never touched. AVI (`.avi`) is recognised as a video but keeps its
 * null metadata: this build does not parse that container, and that is a supported outcome, not an
 * error. A malformed or truncated file falls back to the same kind with null metadata.
 */
class FileMediaProbe : MediaProbe {
    override fun probe(path: Path): MediaProbeResult {
        val fileName = path.fileName.toString()
        val kind = mediaKindOf(fileName)
        if (kind != MediaKindHint.VIDEO) return MediaProbeResult(kind)
        val metadata = when {
            isIsoBaseMedia(fileName) -> readMetadata(path, ::parseMovieBox)
            isMatroska(fileName) -> readMetadata(path, ::parseMatroska)
            else -> null
        }
        return MediaProbeResult(kind, metadata?.durationSeconds, metadata?.width, metadata?.height)
    }

    private fun readMetadata(path: Path, parser: (FileChannel) -> VideoMetadata?): VideoMetadata? =
        try {
            FileChannel.open(path, StandardOpenOption.READ).use(parser)
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

    /**
     * Walks the EBML header and `Segment` and pulls the duration out of `Info` and the video
     * dimensions out of `Tracks`. Every element is advanced over by its size, so `Cluster` and any
     * other payload is never read.
     */
    private fun parseMatroska(channel: FileChannel): VideoMetadata? {
        val fileSize = channel.size()
        val header = readElement(channel, FILE_START, fileSize) ?: return null
        if (header.id != EBML_HEADER_ID) return null
        val segment = findElement(channel, header.payloadEnd, fileSize, SEGMENT_ID) ?: return null
        val info = findElement(channel, segment.payloadStart, segment.payloadEnd, INFO_ID)
        val tracks = findElement(channel, segment.payloadStart, segment.payloadEnd, TRACKS_ID)
        val durationSeconds = info?.let { readMatroskaDuration(channel, it) }
        val dimensions = tracks?.let { readMatroskaDimensions(channel, it) }
        if (durationSeconds == null && dimensions == null) return null
        return VideoMetadata(durationSeconds, dimensions?.first, dimensions?.second)
    }

    /** Duration is in ticks; seconds = ticks × timecodeScale (nanoseconds) ÷ 1e9. */
    private fun readMatroskaDuration(channel: FileChannel, info: EbmlElement): Double? {
        val timecodeScale = findElement(channel, info.payloadStart, info.payloadEnd, TIMECODE_SCALE_ID)
            ?.let { readUnsignedInt(channel, it) }
            ?: DEFAULT_TIMECODE_SCALE
        if (timecodeScale <= 0L) return null
        val duration = findElement(channel, info.payloadStart, info.payloadEnd, DURATION_ID) ?: return null
        val ticks = readFloat(channel, duration) ?: return null
        if (ticks <= 0.0) return null
        return ticks * timecodeScale / NANOS_PER_SECOND
    }

    private fun readMatroskaDimensions(channel: FileChannel, tracks: EbmlElement): Pair<Int, Int>? {
        var position = tracks.payloadStart
        while (position < tracks.payloadEnd) {
            val entry = readElement(channel, position, tracks.payloadEnd) ?: return null
            if (entry.id == TRACK_ENTRY_ID && isVideoTrack(channel, entry)) {
                val video = findElement(channel, entry.payloadStart, entry.payloadEnd, VIDEO_ID)
                val dimensions = video?.let { readPixelDimensions(channel, it) }
                if (dimensions != null) return dimensions
            }
            position = entry.payloadEnd
        }
        return null
    }

    private fun isVideoTrack(channel: FileChannel, entry: EbmlElement): Boolean =
        findElement(channel, entry.payloadStart, entry.payloadEnd, TRACK_TYPE_ID)
            ?.let { readUnsignedInt(channel, it) } == VIDEO_TRACK_TYPE

    private fun readPixelDimensions(channel: FileChannel, video: EbmlElement): Pair<Int, Int>? {
        val width = findElement(channel, video.payloadStart, video.payloadEnd, PIXEL_WIDTH_ID)
            ?.let { readUnsignedInt(channel, it) }
        val height = findElement(channel, video.payloadStart, video.payloadEnd, PIXEL_HEIGHT_ID)
            ?.let { readUnsignedInt(channel, it) }
        if (width == null || height == null || width <= 0L || height <= 0L) return null
        return width.toInt() to height.toInt()
    }

    /**
     * Reads one EBML element header at [position] and returns its range. The element ID keeps its
     * length descriptor; the size is a variable-length integer. An element whose size would run past
     * [limit] is clamped to it, and an unknown size runs to [limit], so a truncated file still walks.
     */
    private fun readElement(channel: FileChannel, position: Long, limit: Long): EbmlElement? {
        val id = readElementId(channel, position, limit) ?: return null
        val size = readElementSize(channel, position + id.length, limit) ?: return null
        val payloadStart = position + id.length + size.length
        if (payloadStart > limit) return null
        val payloadEnd =
            if (size.unknown || size.value > limit - payloadStart) limit else payloadStart + size.value
        if (payloadEnd < payloadStart) return null
        return EbmlElement(id.value, payloadStart, payloadEnd)
    }

    private fun findElement(channel: FileChannel, start: Long, end: Long, id: Long): EbmlElement? {
        var position = start
        while (position < end) {
            val element = readElement(channel, position, end) ?: return null
            if (element.id == id) return element
            if (element.payloadEnd <= position) return null
            position = element.payloadEnd
        }
        return null
    }

    private fun readElementId(channel: FileChannel, position: Long, limit: Long): EbmlId? {
        val window = readWindow(channel, position, limit, MAX_ID_BYTES) ?: return null
        if (window.isEmpty()) return null
        val length = encodedLength(window[0].toInt() and BYTE_MASK)
        if (length == 0 || length > MAX_ID_BYTES || window.size < length) return null
        var id = 0L
        for (index in 0 until length) id = (id shl 8) or (window[index].toLong() and BYTE_MASK_LONG)
        return EbmlId(id, length)
    }

    private fun readElementSize(channel: FileChannel, position: Long, limit: Long): EbmlSize? {
        val window = readWindow(channel, position, limit, MAX_SIZE_BYTES) ?: return null
        if (window.isEmpty()) return null
        val first = window[0].toInt() and BYTE_MASK
        val length = encodedLength(first)
        if (length == 0 || length > MAX_SIZE_BYTES || window.size < length) return null
        val valueMask = (1 shl (8 - length)) - 1
        var value = (first and valueMask).toLong()
        for (index in 1 until length) value = (value shl 8) or (window[index].toLong() and BYTE_MASK_LONG)
        val allOnes = if (length == MAX_SIZE_BYTES) (1L shl 56) - 1 else (1L shl (7 * length)) - 1
        return EbmlSize(value, length, value == allOnes)
    }

    /** The number of bytes a variable-length integer occupies, from the first byte's marker bit. */
    private fun encodedLength(first: Int): Int {
        if (first == 0) return 0
        var length = 1
        var mask = 0x80
        while (first and mask == 0) {
            length++
            mask = mask shr 1
        }
        return length
    }

    private fun readUnsignedInt(channel: FileChannel, element: EbmlElement): Long? {
        val length = (element.payloadEnd - element.payloadStart).toInt()
        if (length <= 0 || length > MAX_SIZE_BYTES) return null
        val bytes = readExact(channel, element.payloadStart, length, element.payloadEnd) ?: return null
        var value = 0L
        for (byte in bytes) value = (value shl 8) or (byte.toLong() and BYTE_MASK_LONG)
        return value
    }

    private fun readFloat(channel: FileChannel, element: EbmlElement): Double? {
        val length = (element.payloadEnd - element.payloadStart).toInt()
        val bytes = readExact(channel, element.payloadStart, length, element.payloadEnd) ?: return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return when (length) {
            FLOAT_32_BYTES -> buffer.getFloat().toDouble()
            FLOAT_64_BYTES -> buffer.getDouble()
            else -> null
        }
    }

    /** Reads up to [maxBytes] bytes at [position] without going past [limit]; the array may be short. */
    private fun readWindow(channel: FileChannel, position: Long, limit: Long, maxBytes: Int): ByteArray? {
        if (position < 0L || position >= limit) return null
        val count = minOf(maxBytes.toLong(), limit - position).toInt()
        if (count <= 0) return null
        val window = ByteArray(count)
        val buffer = ByteBuffer.wrap(window)
        channel.position(position)
        var read = 0
        while (buffer.hasRemaining()) {
            val chunk = channel.read(buffer)
            if (chunk <= 0) break
            read += chunk
        }
        return window.copyOf(read)
    }

    private fun readExact(channel: FileChannel, position: Long, count: Int, limit: Long): ByteArray? {
        if (count <= 0 || position < 0L || position + count > limit) return null
        val bytes = ByteArray(count)
        val buffer = ByteBuffer.wrap(bytes)
        channel.position(position)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) break
        }
        return if (buffer.hasRemaining()) null else bytes
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

        const val BYTE_MASK = 0xFF
        const val BYTE_MASK_LONG = 0xFFL
        const val MAX_ID_BYTES = 4
        const val MAX_SIZE_BYTES = 8
        const val FLOAT_32_BYTES = 4
        const val FLOAT_64_BYTES = 8
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_TIMECODE_SCALE = 1_000_000L
        const val EBML_HEADER_ID = 0x1A45DFA3L
        const val SEGMENT_ID = 0x18538067L
        const val INFO_ID = 0x1549A966L
        const val TIMECODE_SCALE_ID = 0x2AD7B1L
        const val DURATION_ID = 0x4489L
        const val TRACKS_ID = 0x1654AE6BL
        const val TRACK_ENTRY_ID = 0xAEL
        const val TRACK_TYPE_ID = 0x83L
        const val VIDEO_ID = 0xE0L
        const val PIXEL_WIDTH_ID = 0xB0L
        const val PIXEL_HEIGHT_ID = 0xBAL
        const val VIDEO_TRACK_TYPE = 1L
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
private val MATROSKA_EXTENSIONS = setOf("mkv", "webm")

private fun isIsoBaseMedia(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in ISO_EXTENSIONS

private fun isMatroska(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in MATROSKA_EXTENSIONS

private data class Box(val type: String, val payloadStart: Long, val payloadEnd: Long)

private data class BoxHeader(val type: String, val size: Long, val headerSize: Long)

private data class EbmlId(val value: Long, val length: Int)

private data class EbmlSize(val value: Long, val length: Int, val unknown: Boolean)

private data class EbmlElement(val id: Long, val payloadStart: Long, val payloadEnd: Long)

private data class VideoMetadata(val durationSeconds: Double?, val width: Int?, val height: Int?)
