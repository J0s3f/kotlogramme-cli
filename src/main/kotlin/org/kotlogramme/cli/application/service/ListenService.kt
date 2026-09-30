package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.spi.UpdateSource
import org.kotlogramme.cli.domain.IncomingUpdate

/**
 * Follows an [UpdateSource], forwarding each update until the stop predicate asks it to stop.
 *
 * The source is polled with [timeoutMillis]; a `null` poll is a timeout, so the loop simply asks
 * again and only the stop predicate ends it. A quiet stream therefore keeps waiting rather than
 * looking like the end of updates.
 */
class ListenService(
    private val source: UpdateSource,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : Listen {
    override fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int {
        var handled = 0
        while (!stop()) {
            val update = source.next(timeoutMillis) ?: continue
            onUpdate(update)
            handled++
        }
        return handled
    }

    companion object {
        /** Matches the facade's own default, so one poll waits about as long as the client would. */
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
