package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Session

/** The session operations the facade exposes, in domain terms. */
interface SessionGateway {
    fun sessions(): List<Session>

    /** Drops the session [hash] names, which the listing reported. */
    fun terminate(hash: Long)

    /** Drops every session but the one this call is made from. */
    fun terminateAll()
}
