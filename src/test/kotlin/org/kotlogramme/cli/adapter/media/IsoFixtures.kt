package org.kotlogramme.cli.adapter.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds ISO base media bytes in memory so the probe can be exercised without a real recording.
 *
 * The layout mirrors what an encoder writes: `ftyp`, a little `mdat`, then `moov` holding `mvhd`
 * (the duration) and a `trak` whose `tkhd` carries the 16.16 fixed-point dimensions.
 */
internal fun isoVideoBytes(
    timescale: Int = 1000,
    duration: Long = DEFAULT_DURATION,
    width: Int = 1920,
    height: Int = 1080,
): ByteArray {
    val movie = isoBox(
        "moov",
        isoBox("mvhd", isoMvhd(timescale, duration)) +
            isoBox("trak", isoBox("tkhd", isoTkhd(width, height))),
    )
    return isoBox("ftyp", "isom".toByteArray(Charsets.US_ASCII)) +
        isoBox("mdat", ByteArray(64)) +
        movie
}

internal fun isoMvhd(timescale: Int, duration: Long, version: Int = 0): ByteArray {
    val buffer = ByteBuffer.allocate(if (version == 1) MVHD_V1_SIZE else MVHD_V0_SIZE).order(ByteOrder.BIG_ENDIAN)
    buffer.put(version.toByte())
    buffer.put(ByteArray(FLAGS_SIZE))
    if (version == 1) {
        buffer.putLong(0)
        buffer.putLong(0)
        buffer.putInt(timescale)
        buffer.putLong(duration)
    } else {
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(timescale)
        buffer.putInt(duration.toInt())
    }
    return buffer.array()
}

internal fun isoTkhd(width: Int, height: Int, version: Int = 0): ByteArray {
    val widthOffset = if (version == 1) TKHD_WIDTH_OFFSET_V1 else TKHD_WIDTH_OFFSET_V0
    val buffer = ByteBuffer.allocate(if (version == 1) TKHD_V1_SIZE else TKHD_V0_SIZE).order(ByteOrder.BIG_ENDIAN)
    buffer.put(version.toByte())
    buffer.put(ByteArray(FLAGS_SIZE))
    buffer.putInt(widthOffset, width shl 16)
    buffer.putInt(widthOffset + 4, height shl 16)
    return buffer.array()
}

internal fun isoBox(type: String, payload: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(BOX_HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
    buffer.putInt(BOX_HEADER_SIZE + payload.size)
    buffer.put(type.toByteArray(Charsets.US_ASCII))
    buffer.put(payload)
    return buffer.array()
}

/** A box whose declared size is independent of the bytes it carries, to exercise malformed input. */
internal fun isoBoxWithDeclaredSize(type: String, declaredSize: Long, payload: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(BOX_HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
    buffer.putInt(declaredSize.toInt())
    buffer.put(type.toByteArray(Charsets.US_ASCII))
    buffer.put(payload)
    return buffer.array()
}

private const val DEFAULT_DURATION = 2_610_000L
private const val BOX_HEADER_SIZE = 8
private const val FLAGS_SIZE = 3
private const val MVHD_V0_SIZE = 20
private const val MVHD_V1_SIZE = 32
private const val TKHD_V0_SIZE = 84
private const val TKHD_V1_SIZE = 96
private const val TKHD_WIDTH_OFFSET_V0 = 76
private const val TKHD_WIDTH_OFFSET_V1 = 88
