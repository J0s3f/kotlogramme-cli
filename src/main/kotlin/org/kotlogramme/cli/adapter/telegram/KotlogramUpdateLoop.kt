package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TypedUpdate
import com.github.badoualy.telegram.api.UpdatesApi
import org.kotlogramme.cli.application.port.spi.UpdateLoop
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.cli.domain.Message as DomainMessage

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
    private val userNames: UserNameCache,
    private val selfId: () -> Long?,
) : UpdateLoop {
    override fun start(onUpdate: (IncomingUpdate) -> Unit): Boolean =
        updates.startUpdateLoop(callback = { _, update ->
            update.renamedUserId()?.let(userNames::forget)
            onUpdate(update.toIncomingUpdate(userNames::nameOf, selfId))
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
internal fun TypedUpdate.toIncomingUpdate(
    names: (Long) -> String? = { null },
    selfId: () -> Long? = { null },
): IncomingUpdate {
    val message = message ?: return otherUpdate()
    val chat = message.chat(names)
    val domain = message.toMessage().named(message, names, selfId).fromPartnerOf(chat)
    return IncomingUpdate.NewMessage(chat, domain)
}

/** A raw update is named after the Telegram update it carries, and its payload is decoded to JSON. */
private fun TypedUpdate.otherUpdate(): IncomingUpdate.Other = IncomingUpdate.Other(
    kind = rawUpdate?.name ?: kind,
    data = rawUpdate?.toJson().orEmpty(),
    userId = rawUpdate?.userId(),
)

/**
 * The chat a message arrived in. An update that names only a peer id, such as a message you sent
 * from another client to a contact, has no resolved peer, so [names] looks the title up and the id
 * stands in when it cannot.
 */
private fun Message.chat(names: (Long) -> String?): Chat? = peer?.toChat() ?: peerId?.let { id ->
    Chat(
        id = id,
        title = names(id) ?: id.toString(),
        kind = ChatKind.PRIVATE,
        username = null,
        lastMessagePreview = null,
        lastMessageAt = null,
    )
}

/**
 * The sender of an update that names none, or only by id, is looked up; the id stays when it fails.
 * A message you sent from another client names nobody, and its sender is you, so [selfId] fills it in.
 */
private fun DomainMessage.named(source: Message, names: (Long) -> String?, selfId: () -> Long?): DomainMessage {
    if (source.sender != null) return this
    val id = source.senderId ?: selfId.takeIf { source.outgoing }?.invoke() ?: return this
    return copy(senderId = id, senderName = names(id) ?: senderName.ifBlank { id.toString() })
}

/**
 * The user Telegram reports as changed, or `null` for any other update.
 *
 * `updateUserName` carries the new name and `updateUser` only says the user changed, so either one
 * means a cached name for that user is out of date and has to be asked for again.
 */
internal fun TypedUpdate.renamedUserId(): Long? =
    rawUpdate?.takeIf { it.name in USER_CHANGE_UPDATES }?.userId()

private val USER_CHANGE_UPDATES = setOf("updateUserName", "updateUser")

/**
 * An incoming message in a private chat is from the person the chat is with, and that person has one
 * name there: the chat's. The update often names only the chat, and a name built from the sender's
 * own profile can differ from the chat title, so both are made to agree.
 */
private fun DomainMessage.fromPartnerOf(chat: Chat?): DomainMessage {
    val isFromPartner = chat != null && chat.kind == ChatKind.PRIVATE && !outgoing &&
        (senderId == null || senderId == chat.id)
    return if (isFromPartner) copy(senderName = chat.title, senderId = chat.id) else this
}
