package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.UpdateSource
import org.kotlogramme.cli.domain.IncomingUpdate

/**
 * The [UpdateSource] backed by the kotlogramme facade.
 *
 * A facade update carrying a message becomes [IncomingUpdate.NewMessage]; the chat comes from the
 * message's peer and is `null` when the update carries none. Every other kind becomes
 * [IncomingUpdate.Other]. A `null` facade update is a timeout and stays `null`.
 */
internal class KotlogramUpdateSource(
    private val operations: FacadeUpdateOperations,
) : UpdateSource {
    override fun next(timeoutMillis: Long): IncomingUpdate? {
        val update = operations.next(timeoutMillis) ?: return null
        val message = update.message ?: return IncomingUpdate.Other(update.kind)
        return IncomingUpdate.NewMessage(message.peer?.toChat(), message.toMessage())
    }
}
