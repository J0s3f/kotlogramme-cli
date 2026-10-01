package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatAction
import org.kotlogramme.cli.application.port.spi.MessageWriteGateway
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Message

/**
 * The [MessageWriteGateway] backed by the kotlogramme facade.
 *
 * References are resolved through the same [ChatReferenceResolver] the reading stack uses, so a
 * `@username`, an invite link and a numeric id all mean the same chat here as they do elsewhere.
 * Forwarded messages that the facade could not carry over are dropped rather than rendered as a
 * bogus empty message.
 */
internal class KotlogramMessageWriteGateway(
    private val operations: FacadeMessageWriteOperations,
    private val resolver: ChatReferenceResolver,
) : MessageWriteGateway {
    override fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message =
        operations.send(resolver.resolve(reference), text, replyToMessageId, silent).toMessage()

    override fun edit(reference: String, messageId: Int, text: String): Message =
        operations.edit(resolver.resolve(reference), messageId, text).toMessage()

    override fun delete(reference: String, messageIds: List<Int>): Int =
        operations.delete(resolver.resolve(reference), messageIds)

    override fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message> {
        val fromPeer = resolver.resolve(fromReference)
        val toPeer = resolver.resolve(toReference)
        return operations.forward(toPeer, messageIds, fromPeer).mapNotNull { it?.toMessage() }
    }

    override fun pin(reference: String, messageId: Int) = operations.pin(resolver.resolve(reference), messageId)

    override fun unpin(reference: String, messageId: Int) = operations.unpin(resolver.resolve(reference), messageId)

    override fun unpinAll(reference: String) = operations.unpinAll(resolver.resolve(reference))

    override fun pinnedMessage(reference: String): Message? =
        operations.pinned(resolver.resolve(reference))?.toMessage()

    override fun react(reference: String, messageId: Int, emoji: String) =
        operations.react(resolver.resolve(reference), messageId, emoji)

    override fun removeReaction(reference: String, messageId: Int) =
        operations.removeReaction(resolver.resolve(reference), messageId)

    override fun markRead(reference: String) = operations.markRead(resolver.resolve(reference))

    override fun sendChatAction(reference: String, activity: ChatActivity) =
        operations.sendChatAction(resolver.resolve(reference), activity.toFacade())
}

/** The facade action a domain activity names; the mapping lives here so nothing above knows it. */
private fun ChatActivity.toFacade(): ChatAction = when (this) {
    ChatActivity.TYPING -> ChatAction.TYPING
    ChatActivity.UPLOAD_PHOTO -> ChatAction.UPLOAD_PHOTO
    ChatActivity.UPLOAD_DOCUMENT -> ChatAction.UPLOAD_DOCUMENT
    ChatActivity.RECORD_VIDEO -> ChatAction.RECORD_VIDEO
    ChatActivity.UPLOAD_VIDEO -> ChatAction.UPLOAD_VIDEO
    ChatActivity.RECORD_VOICE -> ChatAction.RECORD_VOICE
    ChatActivity.UPLOAD_VOICE -> ChatAction.UPLOAD_VOICE
    ChatActivity.RECORD_VIDEO_NOTE -> ChatAction.RECORD_VIDEO_NOTE
    ChatActivity.UPLOAD_VIDEO_NOTE -> ChatAction.UPLOAD_VIDEO_NOTE
    ChatActivity.CHOOSE_STICKER -> ChatAction.CHOOSE_STICKER
    ChatActivity.CHOOSE_CONTACT -> ChatAction.CHOOSE_CONTACT
    ChatActivity.GEO_LOCATION -> ChatAction.GEO_LOCATION
    ChatActivity.GAME_PLAY -> ChatAction.GAME_PLAY
    ChatActivity.SPEAKING_IN_GROUP_CALL -> ChatAction.SPEAKING_IN_GROUP_CALL
}
