package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.IncomingUpdate

/** The live update stream, in domain terms. */
interface UpdateSource {
    /** The next update, or `null` when [timeoutMillis] elapses without one. */
    fun next(timeoutMillis: Long): IncomingUpdate?
}
