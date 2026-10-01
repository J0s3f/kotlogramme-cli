package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.UploadProgress
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.application.port.spi.UploadProgressSlot
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Writer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Draws an upload's progress as one rewritten line on the terminal.
 *
 * An upload blocks the thread performing it, so [begin] opens a slot and starts a render thread that
 * polls the counter the performer attaches; the upload itself never draws anything. Each reading
 * repaints the same line with a carriage return, and [UploadProgressSlot.close] erases it, so the
 * command's own output starts on a clean line whether the upload finished, failed or was
 * interrupted. Nothing at all is written when the upload ends before the first tick, or when the
 * caller never watched: a bar must not leave an empty line behind.
 *
 * The cursor is never hidden, so there is no cursor state to restore; the line is erased in place and
 * the cursor is left where the next output begins.
 *
 * The bar writes to a [PrintWriter] so it can go to the process's own stdout, the fallback, or to a
 * JLine terminal's writer. JLine uses `WriteConsoleW`, so the block characters and the padding a bar
 * emits stay correct at any console code page; the bytes are otherwise the same, including the
 * carriage returns that rewrite one line.
 */
class UploadProgressBar(
    private val out: PrintWriter,
    private val color: Boolean = false,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val width: Int = DEFAULT_WIDTH,
) : UploadProgressReporter {
    /** Writes to [out], preserving the stream's own charset. */
    constructor(
        out: PrintStream = System.out,
        color: Boolean = false,
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
        width: Int = DEFAULT_WIDTH,
    ) : this(PrintWriter(out, true, out.charset()), color, intervalMillis, width)

    /** Writes to a [writer], such as a JLine terminal's, using the writer's own encoding. */
    constructor(
        writer: Writer,
        color: Boolean = false,
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
        width: Int = DEFAULT_WIDTH,
    ) : this(PrintWriter(writer, true), color, intervalMillis, width)

    override fun begin(totalBytes: Long): UploadProgressSlot =
        TerminalProgressSlot(out, color, intervalMillis, width, totalBytes)

    companion object {
        /** How often the bar repaints, fast enough to look continuous and slow enough to be free. */
        const val DEFAULT_INTERVAL_MILLIS = 100L

        /** How many cells the bar itself occupies. */
        const val DEFAULT_WIDTH = 20
    }
}

/** One open bar: the render thread, the counter and the line currently on the terminal. */
private class TerminalProgressSlot(
    private val out: PrintWriter,
    private val color: Boolean,
    private val intervalMillis: Long,
    private val width: Int,
    private val totalBytes: Long,
) : UploadProgressSlot {
    private val lock = ReentrantLock()

    private val tick = lock.newCondition()

    @Volatile
    private var painted = 0

    private var counter: (() -> UploadProgress?)? = null
    private var paintedReading: UploadProgress? = null
    private var closed = false

    /** A Ctrl-C ends the process through a shutdown hook, which is the last chance to erase the bar. */
    private val shutdownHook = Thread(::close, "upload-progress-hook")

    private val worker = Thread(::render, "upload-progress").apply { isDaemon = true }

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        worker.start()
    }

    override val isWatched: Boolean = true

    override fun follow(counter: () -> UploadProgress?) {
        lock.withLock { this.counter = counter }
    }

    override fun current(): UploadProgress? = lock.withLock { counter?.invoke() }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            tick.signalAll()
        }
        removeShutdownHook()
        worker.join(WORKER_JOIN_MILLIS)
        erase()
    }

    private fun render() {
        while (awaitTick()) {
            val progress = current() ?: continue
            if (progress == paintedReading) continue
            paintedReading = progress
            paint(progress)
        }
    }

    private fun awaitTick(): Boolean = lock.withLock {
        if (closed) return false
        tick.await(intervalMillis, TimeUnit.MILLISECONDS)
        !closed
    }

    private fun paint(progress: UploadProgress) {
        val line = progressLine(progress, totalBytes, color, width)
        lock.withLock {
            // A close that raced the tick already erased the line; repainting would leave it behind.
            if (closed) return
            val padding = " ".repeat((painted - visibleLength(line)).coerceAtLeast(0))
            out.print(CARRIAGE_RETURN + line + padding)
            out.flush()
            painted = visibleLength(line)
        }
    }

    private fun erase() {
        if (painted == 0) return
        out.print(CARRIAGE_RETURN + " ".repeat(painted) + CARRIAGE_RETURN)
        out.flush()
        painted = 0
    }

    private fun removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (error: IllegalStateException) {
            // The JVM is already shutting down, so the hook has run or is about to.
        }
    }

    private companion object {
        const val WORKER_JOIN_MILLIS = 1_000L
    }
}

/**
 * Whether an upload shows a bar.
 *
 * `--no-progress` wins over `--progress`, as `--no-color` wins over `--color`; either flag on its own
 * overrides the terminal test, and without either the bar follows it: on by default where a person is
 * watching, off in a script or a pipeline.
 */
internal fun progressEnabled(progress: Boolean, noProgress: Boolean, terminal: Boolean): Boolean =
    !noProgress && (progress || terminal)

/**
 * The single line a bar shows for [progress].
 *
 * The rendering is decided by the [totalBytes] the bar was opened with, not by whatever a later
 * reading carries: a caller that declared a total gets a percentage and a bar, and one that did not
 * gets a plain count of the bytes sent with the elapsed time, because a percentage of an unknown
 * whole would be a lie. With [color] off every character is ASCII.
 */
internal fun progressLine(
    progress: UploadProgress,
    totalBytes: Long,
    color: Boolean,
    width: Int = UploadProgressBar.DEFAULT_WIDTH,
): String = when {
    totalBytes > 0 -> barLine(progress, totalBytes, color, width)
    else -> countLine(progress, color)
}

private fun barLine(progress: UploadProgress, totalBytes: Long, color: Boolean, width: Int): String {
    val fraction = (progress.bytesSent.toDouble() / totalBytes).coerceIn(0.0, 1.0)
    val filled = (fraction * width).toInt().coerceIn(0, width)
    val done = styled(fillCharacter(color).repeat(filled), GREEN, color)
    val left = emptyCharacter(color).repeat(width - filled)
    val percent = styled("${progress.percentOf(fraction)}%", CYAN, color)
    return "[$done$left] $percent ${formatBytes(progress.bytesSent)}/${formatBytes(totalBytes)} " +
        formatRate(progress.bytesPerSecond)
}

private fun countLine(progress: UploadProgress, color: Boolean): String =
    "${styled(formatBytes(progress.bytesSent), CYAN, color)} " +
        "${formatRate(progress.bytesPerSecond)} ${formatElapsed(progress.elapsedMillis)}"

private fun UploadProgress.percentOf(fraction: Double): Int = (fraction * 100).toInt()

private fun styled(text: String, color: String, enabled: Boolean): String =
    if (enabled) color + text + RESET else text

/** A byte count with a unit, at one decimal below ten and whole above it. */
private fun formatBytes(bytes: Long): String {
    val (value, unit) = scale(bytes.toDouble())
    return if (value < 10 && unit != BYTE) decimal(value) + " " + unit else whole(value) + " " + unit
}

/** A transfer rate; zero before any time has passed reads as `0 B/s` rather than as a division. */
private fun formatRate(bytesPerSecond: Double): String = formatBytes(bytesPerSecond.toLong()) + "/s"

/** Elapsed time as `m:ss`, or `h:mm:ss` once it passes an hour. */
private fun formatElapsed(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val clock = "%d:%02d".format(Locale.ROOT, minutes % MINUTES_PER_HOUR, seconds % SECONDS_PER_MINUTE)
    return if (hours == 0L) clock else "$hours:$clock"
}

private fun scale(bytes: Double): Pair<Double, String> {
    var value = bytes
    var unit = BYTE
    for (next in UNITS) {
        if (value < STEP || next == UNITS.last()) return value to unit
        value /= STEP
        unit = next
    }
    return value to unit
}

private fun decimal(value: Double): String = "%.1f".format(Locale.ROOT, value)

private fun whole(value: Double): String = "%.0f".format(Locale.ROOT, value)

/** The block characters need a font the client cannot assume, so without colour the bar is ASCII. */
private fun fillCharacter(color: Boolean): String = if (color) BLOCK else HASH

private fun emptyCharacter(color: Boolean): String = if (color) SHADE else DASH

private const val CARRIAGE_RETURN = "\r"
private const val STEP = 1024.0
private const val BYTE = "B"
private val UNITS = listOf("KB", "MB", "GB", "TB")
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HASH = "#"
private const val DASH = "-"
private const val BLOCK = "\u2588"
private const val SHADE = "\u2591"
private const val GREEN = "\u001B[32m"
private const val CYAN = "\u001B[36m"
private const val RESET = "\u001B[0m"
