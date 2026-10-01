package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramUpdate
import com.github.badoualy.telegram.api.TypedUpdate
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
    override fun next(timeoutMillis: Long): IncomingUpdate? =
        operations.next(timeoutMillis)?.toIncomingUpdate()
}

/**
 * The one mapping from a facade update to a domain one.
 *
 * [KotlogramUpdateSource] and [KotlogramUpdateLoop] read the same stream through different waits, so
 * they share this rather than each carrying their own translation.
 */
internal fun TelegramUpdate.toIncomingUpdate(): IncomingUpdate? {
    val message = message ?: return IncomingUpdate.Other(kind)
    return IncomingUpdate.NewMessage(message.peer?.toChat(), message.toMessage())
}

/**
 * [KotlogramUpdateSource.toIncomingUpdate] for the typed projection the background loop delivers.
 *
 * The loop dispatches the whole projection rather than the two-field compatibility view; the mapping
 * is the same, so only the message and the kind are read off it.
 */
internal fun TypedUpdate.toIncomingUpdate(): IncomingUpdate? {
    val message = message ?: return IncomingUpdate.Other(kind)
    return IncomingUpdate.NewMessage(message.peer?.toChat(), message.toMessage())
}
