package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.IncomingUpdate

/** Follow the live update stream. */
interface Listen {
    /**
     * Calls [onUpdate] for each update until [stop] returns true or the source reports no more
     * updates, and returns how many updates were handled.
     *
     * The source may block between updates, so [stop] is a cooperative signal (typically a Ctrl-C
     * flag) rather than an interrupt.
     */
    fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int
}
