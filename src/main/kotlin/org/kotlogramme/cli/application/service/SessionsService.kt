package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.spi.SessionGateway
import org.kotlogramme.cli.domain.Session

/** Lists and terminates active sessions, rejecting a hash that cannot identify one. */
class SessionsService(private val gateway: SessionGateway) : Sessions {
    override fun list(): List<Session> = gateway.sessions()

    override fun terminate(hash: Long) {
        require(hash != 0L) { "a session hash is required; run `sessions` to list them" }
        gateway.terminate(hash)
    }

    override fun terminateAll() = gateway.terminateAll()
}
