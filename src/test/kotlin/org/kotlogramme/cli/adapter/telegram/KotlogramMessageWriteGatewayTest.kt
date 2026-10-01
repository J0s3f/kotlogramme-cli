package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatAction
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import org.kotlogramme.cli.domain.ChatActivity
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramMessageWriteGatewayTest {
    private val ada = peer(id = 7, kind = "user", username = "ada", name = "Ada")

    @Test
    fun `sendText resolves the reference and forwards the send options`() {
        val operations = FakeMessageWriteOperations().apply {
            sentMessage = message(id = 42, text = "hi", outgoing = true, date = 1_000)
        }

        val sent = gatewayWith(operations, ada).sendText("@ada", "hi", replyToMessageId = 5, silent = true)

        assertEquals(SendCall(ada, "hi", 5, true), operations.sends.single())
        assertEquals(42, sent.id)
        assertEquals("hi", sent.text)
        assertEquals(true, sent.outgoing)
    }

    @Test
    fun `edit resolves the reference and maps the read-back message`() {
        val operations = FakeMessageWriteOperations().apply {
            editedMessage = message(id = 42, text = "edited", date = 2_000)
        }

        val edited = gatewayWith(operations, ada).edit("@ada", messageId = 42, text = "edited")

        assertEquals(EditCall(ada, 42, "edited"), operations.edits.single())
        assertEquals(42, edited.id)
        assertEquals("edited", edited.text)
    }

    @Test
    fun `delete resolves the reference and returns the deleted count`() {
        val operations = FakeMessageWriteOperations().apply { deleteCount = 2 }

        val deleted = gatewayWith(operations, ada).delete("@ada", listOf(1, 2))

        assertEquals(DeleteCall(ada, listOf(1, 2)), operations.deletes.single())
        assertEquals(2, deleted)
    }

    @Test
    fun `forward resolves both references and maps the forwarded messages`() {
        val fromPeer = peer(id = 7, kind = "user", name = "Ada")
        val toPeer = peer(id = -9, kind = "group", name = "The Club")
        val chatOperations = FakeChatOperations().apply {
            dialogs = listOf(dialog(fromPeer), dialog(toPeer))
        }
        val operations = FakeMessageWriteOperations().apply {
            forwardedMessages = listOf(
                message(id = 3, text = "carried", date = 3_000),
                null,
            )
        }

        val forwarded = KotlogramMessageWriteGateway(operations, ChatReferenceResolver(chatOperations))
            .forward(fromReference = "7", messageIds = listOf(3), toReference = "-9")

        assertEquals(ForwardCall(toPeer, listOf(3), fromPeer), operations.forwards.single())
        assertEquals(listOf(3), forwarded.map { it.id })
        assertEquals(listOf("carried"), forwarded.map { it.text })
    }

    @Test
    fun `pin forwards the resolved peer and the message id`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).pin("@ada", 42)

        assertEquals(MessageCall(ada, 42), operations.pins.single())
    }

    @Test
    fun `unpin forwards the resolved peer and the message id`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).unpin("@ada", 42)

        assertEquals(MessageCall(ada, 42), operations.unpins.single())
    }

    @Test
    fun `unpinAll forwards the resolved peer`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).unpinAll("@ada")

        assertEquals(listOf(ada), operations.unpinAlls)
    }

    @Test
    fun `pinnedMessage maps the pinned message`() {
        val operations = FakeMessageWriteOperations().apply {
            pinnedMessage = message(id = 42, text = "pinned", date = 1_000)
        }

        val pinned = gatewayWith(operations, ada).pinnedMessage("@ada")

        assertEquals(listOf(ada), operations.pinnedRequests)
        assertEquals(42, pinned?.id)
        assertEquals("pinned", pinned?.text)
    }

    @Test
    fun `pinnedMessage is null when the facade reports none`() {
        val operations = FakeMessageWriteOperations().apply { pinnedMessage = null }

        val pinned = gatewayWith(operations, ada).pinnedMessage("@ada")

        assertEquals(null, pinned)
    }

    @Test
    fun `react forwards the resolved peer, the message id and the emoji`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).react("@ada", 42, "\uD83D\uDC4D")

        assertEquals(ReactionCall(ada, 42, "\uD83D\uDC4D"), operations.reactions.single())
    }

    @Test
    fun `removeReaction forwards the resolved peer and the message id`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).removeReaction("@ada", 42)

        assertEquals(MessageCall(ada, 42), operations.removedReactions.single())
    }

    @Test
    fun `markRead forwards the resolved peer`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).markRead("@ada")

        assertEquals(listOf(ada), operations.markReads)
    }

    @Test
    fun `sendChatAction resolves the reference and maps the activity`() {
        val operations = FakeMessageWriteOperations()

        gatewayWith(operations, ada).sendChatAction("@ada", ChatActivity.UPLOAD_PHOTO)

        assertEquals(
            listOf(FacadeChatActionCall(ada, ChatAction.UPLOAD_PHOTO)),
            operations.chatActions,
        )
    }

    @Test
    fun `every domain activity maps to a facade action`() {
        val operations = FakeMessageWriteOperations()
        val gateway = gatewayWith(operations, ada)

        ChatActivity.entries.forEach { gateway.sendChatAction("@ada", it) }

        assertEquals(ChatActivity.entries.size, operations.chatActions.size)
    }

    private fun gatewayWith(
        operations: FakeMessageWriteOperations,
        facadePeer: TelegramPeer,
    ): KotlogramMessageWriteGateway =
        KotlogramMessageWriteGateway(
            operations,
            ChatReferenceResolver(FakeChatOperations().apply { resolvedPeer = facadePeer }),
        )
}

internal data class SendCall(
    val peer: TelegramPeer,
    val text: String,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal data class EditCall(val peer: TelegramPeer, val id: Int, val text: String)

internal data class DeleteCall(val peer: TelegramPeer, val ids: List<Int>)

internal data class ForwardCall(val toPeer: TelegramPeer, val ids: List<Int>, val fromPeer: TelegramPeer)

internal data class MessageCall(val peer: TelegramPeer, val id: Int)

internal data class ReactionCall(val peer: TelegramPeer, val id: Int, val emoji: String)

internal data class FacadeChatActionCall(val peer: TelegramPeer, val action: ChatAction)

internal class FakeMessageWriteOperations : FacadeMessageWriteOperations {
    var sentMessage: Message = message(id = 1)
    var editedMessage: Message = message(id = 1)
    var deleteCount: Int = 0
    var forwardedMessages: List<Message?> = emptyList()
    var pinnedMessage: Message? = message(id = 1)

    val sends = mutableListOf<SendCall>()
    val edits = mutableListOf<EditCall>()
    val deletes = mutableListOf<DeleteCall>()
    val forwards = mutableListOf<ForwardCall>()
    val pins = mutableListOf<MessageCall>()
    val unpins = mutableListOf<MessageCall>()
    val unpinAlls = mutableListOf<TelegramPeer>()
    val pinnedRequests = mutableListOf<TelegramPeer>()
    val reactions = mutableListOf<ReactionCall>()
    val removedReactions = mutableListOf<MessageCall>()
    val markReads = mutableListOf<TelegramPeer>()
    val chatActions = mutableListOf<FacadeChatActionCall>()

    override fun send(peer: TelegramPeer, text: String, replyToMessageId: Int?, silent: Boolean): Message {
        sends += SendCall(peer, text, replyToMessageId, silent)
        return sentMessage
    }

    override fun edit(peer: TelegramPeer, id: Int, text: String): Message {
        edits += EditCall(peer, id, text)
        return editedMessage
    }

    override fun delete(peer: TelegramPeer, ids: Collection<Int>): Int {
        deletes += DeleteCall(peer, ids.toList())
        return deleteCount
    }

    override fun forward(toPeer: TelegramPeer, ids: Collection<Int>, fromPeer: TelegramPeer): List<Message?> {
        forwards += ForwardCall(toPeer, ids.toList(), fromPeer)
        return forwardedMessages
    }

    override fun pin(peer: TelegramPeer, id: Int) {
        pins += MessageCall(peer, id)
    }

    override fun unpin(peer: TelegramPeer, id: Int) {
        unpins += MessageCall(peer, id)
    }

    override fun unpinAll(peer: TelegramPeer) {
        unpinAlls += peer
    }

    override fun pinned(peer: TelegramPeer): Message? {
        pinnedRequests += peer
        return pinnedMessage
    }

    override fun react(peer: TelegramPeer, id: Int, emoji: String) {
        reactions += ReactionCall(peer, id, emoji)
    }

    override fun removeReaction(peer: TelegramPeer, id: Int) {
        removedReactions += MessageCall(peer, id)
    }

    override fun markRead(peer: TelegramPeer) {
        markReads += peer
    }

    override fun sendChatAction(peer: TelegramPeer, action: ChatAction) {
        chatActions += FacadeChatActionCall(peer, action)
    }
}
