package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TypedUpdate
import com.github.badoualy.telegram.api.UpdatesApi
import org.kotlogramme.cli.application.port.spi.UpdateLoop
import org.kotlogramme.cli.domain.IncomingUpdate

/**
 * The [UpdateLoop] backed by the facade's own background loop.
 *
 * The facade owns the thread, polls at its short default and joins on stop, so the adapter only
 * translates: an update off the shared stream becomes an [IncomingUpdate], and one with no message
 * becomes [IncomingUpdate.Other]. A callback that throws ends the facade loop; the port has no way
 * to report that, so the failure is left where the facade records it rather than swallowed.
 */
internal class KotlogramUpdateLoop(
    private val updates: UpdatesApi,
) : UpdateLoop {
    override fun start(onUpdate: (IncomingUpdate) -> Unit): Boolean =
        updates.startUpdateLoop(callback = { _, update ->
            onUpdate(update.toIncomingUpdate())
        })

    override fun stop() = updates.stopUpdateLoop()

    override val isRunning: Boolean get() = updates.isUpdateLoopRunning()
}

/**
 * Maps an update off the facade's background loop to a domain one.
 *
 * A message update becomes [IncomingUpdate.NewMessage], with the chat from the message's peer and
 * `null` when it carries none; every other kind becomes [IncomingUpdate.Other].
 */
internal fun TypedUpdate.toIncomingUpdate(): IncomingUpdate {
    val message = message ?: return IncomingUpdate.Other(kind)
    return IncomingUpdate.NewMessage(message.peer?.toChat(), message.toMessage())
}
