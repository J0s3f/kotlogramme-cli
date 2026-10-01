package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Session

/**
 * The account's active sessions.
 *
 * Termination is irreversible and security-sensitive: [terminate] takes the exact [Session.hash]
 * the listing reported and never guesses, and [terminateAll] drops every session but the one the
 * call is made from.
 */
interface Sessions {
    fun list(): List<Session>

    fun terminate(hash: Long)

    fun terminateAll()
}
