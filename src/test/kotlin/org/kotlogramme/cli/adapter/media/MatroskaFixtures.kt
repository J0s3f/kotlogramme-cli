package org.kotlogramme.cli.adapter.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds Matroska/EBML bytes in memory so the probe can be exercised without a real recording.
 *
 * The layout mirrors what a muxer writes: an EBML `Header`, then a `Segment` holding `Info` (the
 * `TimecodeScale` and the `Duration` in ticks), `Tracks` (a `TrackEntry` with a `Video` child) and a
 * `Cluster` of dummy payload that a correct reader must skip rather than walk.
 */
internal fun mkvFileBytes(
    timecodeScale: Long = DEFAULT_TIMECODE_SCALE,
    durationTicks: Double = DEFAULT_DURATION_TICKS,
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    includeDuration: Boolean = true,
    includeTracks: Boolean = true,
    includeVideoTrack: Boolean = true,
    singlePrecisionDuration: Boolean = false,
    clusterFirst: Boolean = false,
    clusterBytes: Int = DEFAULT_CLUSTER_BYTES,
): ByteArray {
    var infoPayload = ebmlElement(TIMECODE_SCALE_ID, uintBytes(timecodeScale))
    if (includeDuration) {
        val duration = if (singlePrecisionDuration) {
            floatBytes(durationTicks)
        } else {
            doubleBytes(durationTicks)
        }
        infoPayload += ebmlElement(DURATION_ID, duration)
    }
    val body = if (includeTracks) {
        infoPayload.let { ebmlElement(INFO_ID, it) } +
            ebmlElement(TRACKS_ID, tracksBytes(width, height, includeVideoTrack))
    } else {
        ebmlElement(INFO_ID, infoPayload)
    }
    val cluster = ebmlElement(CLUSTER_ID, ByteArray(clusterBytes))
    val segmentPayload = if (clusterFirst) cluster + body else body + cluster
    return ebmlElement(EBML_HEADER_ID, headerBytes()) + ebmlElement(SEGMENT_ID, segmentPayload)
}

private fun tracksBytes(width: Int, height: Int, includeVideoTrack: Boolean): ByteArray =
    if (includeVideoTrack) {
        val video = ebmlElement(
            VIDEO_ID,
            ebmlElement(PIXEL_WIDTH_ID, uintBytes(width.toLong())) +
                ebmlElement(PIXEL_HEIGHT_ID, uintBytes(height.toLong())),
        )
        ebmlElement(TRACK_ENTRY_ID, ebmlElement(TRACK_TYPE_ID, uintBytes(VIDEO_TRACK_TYPE)) + video)
    } else {
        ebmlElement(
            TRACK_ENTRY_ID,
            ebmlElement(TRACK_TYPE_ID, uintBytes(AUDIO_TRACK_TYPE)) +
                ebmlElement(AUDIO_ID, ByteArray(AUDIO_FILL)),
        )
    }

private fun headerBytes(): ByteArray =
    ebmlElement(EBML_VERSION_ID, uintBytes(1)) +
        ebmlElement(DOC_TYPE_ID, "matroska".toByteArray(Charsets.US_ASCII))

/** One EBML element: its ID bytes (with length descriptor), its size VINT and its payload. */
internal fun ebmlElement(id: Long, payload: ByteArray): ByteArray =
    idBytes(id) + sizeBytes(payload.size) + payload

private fun idBytes(id: Long): ByteArray {
    var length = 0
    var shifted = id
    while (shifted != 0L) {
        length++
        shifted = shifted ushr 8
    }
    if (length == 0) length = 1
    val bytes = ByteArray(length)
    var remaining = id
    for (index in length - 1 downTo 0) {
        bytes[index] = (remaining and 0xFFL).toByte()
        remaining = remaining ushr 8
    }
    return bytes
}

private fun sizeBytes(size: Int): ByteArray {
    val value = size.toLong()
    var length = 1
    while (length < 8 && value > (1L shl (7 * length)) - 2) length++
    val bytes = ByteArray(length)
    var remaining = value
    for (index in length - 1 downTo 0) {
        bytes[index] = (remaining and 0xFFL).toByte()
        remaining = remaining ushr 8
    }
    bytes[0] = (bytes[0].toInt() or (1 shl (8 - length))).toByte()
    return bytes
}

private fun uintBytes(value: Long): ByteArray {
    if (value == 0L) return byteArrayOf(0)
    var length = 0
    var shifted = value
    while (shifted != 0L) {
        length++
        shifted = shifted ushr 8
    }
    val bytes = ByteArray(length)
    var remaining = value
    for (index in length - 1 downTo 0) {
        bytes[index] = (remaining and 0xFFL).toByte()
        remaining = remaining ushr 8
    }
    return bytes
}

private fun doubleBytes(value: Double): ByteArray =
    ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(value).array()

private fun floatBytes(value: Double): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value.toFloat()).array()

private const val DEFAULT_TIMECODE_SCALE = 1_000_000L
private const val DEFAULT_DURATION_TICKS = 5_000.0
private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 720
private const val DEFAULT_CLUSTER_BYTES = 32
private const val AUDIO_FILL = 8
private const val VIDEO_TRACK_TYPE = 1L
private const val AUDIO_TRACK_TYPE = 2L

private const val EBML_VERSION_ID = 0x4286L
private const val DOC_TYPE_ID = 0x4282L
private const val EBML_HEADER_ID = 0x1A45DFA3L
private const val SEGMENT_ID = 0x18538067L
private const val INFO_ID = 0x1549A966L
private const val TIMECODE_SCALE_ID = 0x2AD7B1L
private const val DURATION_ID = 0x4489L
private const val TRACKS_ID = 0x1654AE6BL
private const val TRACK_ENTRY_ID = 0xAEL
private const val TRACK_TYPE_ID = 0x83L
private const val VIDEO_ID = 0xE0L
private const val AUDIO_ID = 0xE1L
private const val PIXEL_WIDTH_ID = 0xB0L
private const val PIXEL_HEIGHT_ID = 0xBAL
private const val CLUSTER_ID = 0x1F43B675L
