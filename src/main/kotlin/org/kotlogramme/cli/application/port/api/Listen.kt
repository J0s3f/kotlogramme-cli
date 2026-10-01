package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.IncomingUpdate

/** Follow the live update stream. */
interface Listen {
    /**
     * Calls [onUpdate] for each update until [stop] returns true or the source reports no more
     * updates, and returns how many updates were handled.
     *
     * The stream is followed on a background loop the adapter owns, so [stop] is checked between the
     * loop's short polls: a Ctrl-C flag makes the command return promptly instead of waiting out the
     * facade's 30 s poll. [stop] is a cooperative signal (typically a Ctrl-C flag), not an interrupt.
     */
    fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int
}
