package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.spi.UpdateLoop
import org.kotlogramme.cli.domain.IncomingUpdate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives an [UpdateLoop] until the stop predicate asks it to stop.
 *
 * The service owns no thread of its own: [UpdateLoop.start] puts the facade's loop on one and
 * [UpdateLoop.stop] joins it, so this waits for the loop rather than polling the stream. The wait
 * ends when the stop predicate says so - checked after every update the loop delivers and again
 * between the loop's short polls, so a quiet stream still answers a stop promptly - which costs the
 * facade's short poll rather than its 30 s one.
 *
 * Returns how many updates were handled, [onUpdate] being called for each.
 */
class ListenService(private val loop: UpdateLoop) : Listen {
    override fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int {
        val handled = AtomicInteger(0)
        val stopped = AtomicBoolean(false)
        loop.start { update ->
            onUpdate(update)
            handled.incrementAndGet()
            if (stop()) stopped.set(true)
        }
        // The loop owns the thread and the stop joins it, so the wait is for the loop to end and not
        // for one poll: a quiet source keeps the loop polling in short waits, and the predicate is
        // checked between them.
        while (!stop() && !stopped.get() && loop.isRunning) {
            Thread.sleep(IDLE_WAIT_MILLIS)
        }
        loop.stop()
        return handled.get()
    }

    companion object {
        /** How often the predicate is checked while the loop is quiet; well under its poll. */
        private const val IDLE_WAIT_MILLIS = 10L
    }
}
