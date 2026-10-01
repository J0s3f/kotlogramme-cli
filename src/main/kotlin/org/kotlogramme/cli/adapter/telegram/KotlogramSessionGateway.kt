package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.SessionGateway
import org.kotlogramme.cli.domain.Session

/**
 * The [SessionGateway] backed by the kotlogramme facade.
 *
 * The listing keeps the order Telegram reports, which puts the current session among the others so
 * the `current` column is what marks it; no session is filtered out.
 */
internal class KotlogramSessionGateway(private val operations: FacadeSessionOperations) : SessionGateway {
    override fun sessions(): List<Session> =
        operations.authorizations().authorizations.map { it.toSession() }

    override fun terminate(hash: Long) = operations.resetAuthorization(hash)

    override fun terminateAll() = operations.resetAuthorizations()
}
