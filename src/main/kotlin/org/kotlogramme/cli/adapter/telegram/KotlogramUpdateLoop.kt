package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TypedUpdate
import com.github.badoualy.telegram.api.UpdatesApi
import org.kotlogramme.cli.application.port.spi.UpdateLoop
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
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
    val message = message ?: return otherUpdate()
    return IncomingUpdate.NewMessage(message.chat(), message.toMessage())
}

/** A raw update is named after the Telegram update it carries, and its payload is decoded to JSON. */
private fun TypedUpdate.otherUpdate(): IncomingUpdate.Other = IncomingUpdate.Other(
    kind = rawUpdate?.name ?: kind,
    data = rawUpdate?.toJson().orEmpty(),
)

/**
 * The chat a message arrived in. An update that names only a peer id, such as a message you sent
 * from another client to a contact, has no resolved peer, so the id stands in for the title.
 */
private fun Message.chat(): Chat? = peer?.toChat() ?: peerId?.let { id ->
    Chat(
        id = id,
        title = id.toString(),
        kind = ChatKind.PRIVATE,
        username = null,
        lastMessagePreview = null,
        lastMessageAt = null,
    )
}
