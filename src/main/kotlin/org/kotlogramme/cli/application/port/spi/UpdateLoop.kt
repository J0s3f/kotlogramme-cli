package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.IncomingUpdate

/**
 * The update stream followed on the library's own background thread.
 *
 * The thread is a loop the adapter owns, so a stop joins it and costs at most the short wait one
 * iteration takes rather than the whole poll a one-at-a-time read would cost. The port is expressed
 * only in domain terms: no poll length, no library type, and nothing about how the thread is created
 * reaches the application.
 *
 * [start] is idempotent while a loop runs, so a caller cannot put a second reader on the one shared
 * stream by starting twice. [stop] stops a loop that is not running without failing.
 */
interface UpdateLoop {
    /**
     * Starts calling [onUpdate] for each update on a background thread, returning whether the loop
     * was started. A loop that is already running is left alone and the call reports `false`.
     */
    fun start(onUpdate: (IncomingUpdate) -> Unit): Boolean

    /** Stops the loop and waits for its thread. Stopping a loop that is not running does nothing. */
    fun stop()

    /** Whether a loop started by [start] is still running. */
    val isRunning: Boolean
}
