package org.kotlogramme.cli.application.port.spi

/**
 * How far an upload in flight has got, in domain terms.
 *
 * [bytesSent] is what has been handed to Telegram so far and [totalBytes] what the upload declared
 * up front; a total of zero means it is not known, which is why [fraction] and [percent] are null
 * rather than a made-up zero. [elapsedMillis] is how long the upload has been running, which is all
 * a rate needs: [bytesPerSecond] is derived here rather than carried, so every renderer measures a
 * transfer the same way.
 */
data class UploadProgress(
    val bytesSent: Long,
    val totalBytes: Long,
    val elapsedMillis: Long,
) {
    /** Whether [totalBytes] was declared, which is what decides between a bar and a plain count. */
    val hasTotal: Boolean get() = totalBytes > 0

    /** The share sent, from 0.0 to 1.0, or null when the total is not known. */
    val fraction: Double? get() =
        if (hasTotal) (bytesSent.toDouble() / totalBytes).coerceIn(0.0, 1.0) else null

    /** The whole-number percentage sent, or null when the total is not known. */
    val percent: Int? get() = fraction?.times(100)?.toInt()

    /** The average rate since the upload started, zero before any time has passed. */
    val bytesPerSecond: Double get() =
        if (elapsedMillis > 0) bytesSent * MILLIS_PER_SECOND / elapsedMillis else 0.0

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}

/**
 * The progress of one upload, as something watching it from another thread sees it.
 *
 * An upload blocks until its bytes are across, so a bar cannot be drawn by the code performing it.
 * The performer attaches its own counter with [follow] as soon as it has one — a slot opened before
 * the upload starts has nothing to read yet — and the watcher polls [current] meanwhile.
 */
interface UploadProgressSlot {
    /** Whether anyone is watching, so a performer can skip the work that only feeds the bar. */
    val isWatched: Boolean

    /** Attaches the counter the bar reads; a later call replaces the earlier one. */
    fun follow(counter: () -> UploadProgress?)

    /** The latest reading, or null while no counter is attached. */
    fun current(): UploadProgress?

    /** Ends the bar, whether the upload finished or failed. Calling it twice does nothing. */
    fun close()
}

/**
 * Where an upload reports itself while it is in flight.
 *
 * Whoever performs an upload asks for a slot before the first byte moves and closes it when the
 * upload ends, which is what lets the bar clean up on the success, the failure and the interrupt
 * path alike. [SILENT] is the reporter for a run that shows nothing at all.
 */
interface UploadProgressReporter {
    /** Opens a slot for an upload of [totalBytes] bytes, or of an unknown total when that is zero. */
    fun begin(totalBytes: Long): UploadProgressSlot

    companion object {
        /**
         * The reporter for a run that shows no bar: a script, a pipe, or any caller that turned the
         * bar off. It opens a slot that watches nothing, so a performer can leave it alone.
         */
        val SILENT: UploadProgressReporter = SilentUploadProgressReporter
    }
}

private object SilentUploadProgressReporter : UploadProgressReporter {
    override fun begin(totalBytes: Long): UploadProgressSlot = SilentUploadProgressSlot
}

private object SilentUploadProgressSlot : UploadProgressSlot {
    override val isWatched: Boolean = false

    override fun follow(counter: () -> UploadProgress?) = Unit

    override fun current(): UploadProgress? = null

    override fun close() = Unit
}
