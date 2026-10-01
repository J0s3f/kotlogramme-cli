package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.MessageWriteGateway
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageWritingServiceTest {
    @Test
    fun `sendText passes the valid input through unchanged`() {
        val gateway = FakeMessageWriteGateway()

        val sent = MessageWritingService(gateway).sendText("@ada", "hi", replyToMessageId = 5, silent = true)

        assertEquals(SendCall("@ada", "hi", 5, true), gateway.sends.single())
        assertEquals(gateway.sent, sent)
    }

    @Test
    fun `edit passes the valid input through unchanged`() {
        val gateway = FakeMessageWriteGateway()

        val edited = MessageWritingService(gateway).edit("@ada", messageId = 7, text = "edited")

        assertEquals(EditCall("@ada", 7, "edited"), gateway.edits.single())
        assertEquals(gateway.edited, edited)
    }

    @Test
    fun `delete passes the valid input through unchanged`() {
        val gateway = FakeMessageWriteGateway()

        val deleted = MessageWritingService(gateway).delete("@ada", listOf(1, 2))

        assertEquals(DeleteCall("@ada", listOf(1, 2)), gateway.deletes.single())
        assertEquals(gateway.deleted, deleted)
    }

    @Test
    fun `forward passes the valid input through unchanged`() {
        val gateway = FakeMessageWriteGateway()

        val forwarded = MessageWritingService(gateway).forward("@ada", listOf(1, 2), "@bob")

        assertEquals(ForwardCall("@ada", listOf(1, 2), "@bob"), gateway.forwards.single())
        assertEquals(gateway.forwarded, forwarded)
    }

    @Test
    fun `the void operations pass the valid input through unchanged`() {
        val gateway = FakeMessageWriteGateway()

        MessageWritingService(gateway).apply {
            pin("@ada", 7)
            unpin("@ada", 8)
            react("@ada", 9, "\uD83D\uDC4D")
            removeReaction("@ada", 10)
            markRead("@ada")
            sendChatAction("@ada", ChatActivity.TYPING)
        }

        assertEquals(IdCall("@ada", 7), gateway.pins.single())
        assertEquals(IdCall("@ada", 8), gateway.unpins.single())
        assertEquals(ReactCall("@ada", 9, "\uD83D\uDC4D"), gateway.reactions.single())
        assertEquals(IdCall("@ada", 10), gateway.removedReactions.single())
        assertEquals(listOf("@ada"), gateway.markReads)
        assertEquals(listOf(ChatActionCall("@ada", ChatActivity.TYPING)), gateway.chatActions)
    }

    @Test
    fun `rejects a blank sendText before the gateway`() {
        val gateway = FakeMessageWriteGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            MessageWritingService(gateway).sendText("@ada", "   ", null, silent = false)
        }

        assertTrue(error.message.orEmpty().contains("must not be blank"))
        assertEquals(emptyList(), gateway.sends)
    }

    @Test
    fun `rejects a blank edit before the gateway`() {
        val gateway = FakeMessageWriteGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            MessageWritingService(gateway).edit("@ada", 7, "")
        }

        assertTrue(error.message.orEmpty().contains("must not be blank"))
        assertEquals(emptyList(), gateway.edits)
    }

    @Test
    fun `rejects a non-positive reply-to id before the gateway`() {
        val gateway = FakeMessageWriteGateway()

        assertFailsWith<IllegalArgumentException> {
            MessageWritingService(gateway).sendText("@ada", "hi", replyToMessageId = 0, silent = false)
        }

        assertEquals(emptyList(), gateway.sends)
    }

    @Test
    fun `rejects an empty id list before the gateway`() {
        val gateway = FakeMessageWriteGateway()

        assertFailsWith<IllegalArgumentException> { MessageWritingService(gateway).delete("@ada", emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            MessageWritingService(gateway).forward("@ada", emptyList(), "@bob")
        }

        assertEquals(emptyList(), gateway.deletes)
        assertEquals(emptyList(), gateway.forwards)
    }

    @Test
    fun `rejects a non-positive id before the gateway`() {
        val gateway = FakeMessageWriteGateway()
        val service = MessageWritingService(gateway)

        assertFailsWith<IllegalArgumentException> { service.edit("@ada", 0, "hi") }
        assertFailsWith<IllegalArgumentException> { service.delete("@ada", listOf(1, -1)) }
        assertFailsWith<IllegalArgumentException> { service.forward("@ada", listOf(0), "@bob") }
        assertFailsWith<IllegalArgumentException> { service.pin("@ada", 0) }
        assertFailsWith<IllegalArgumentException> { service.unpin("@ada", -2) }
        assertFailsWith<IllegalArgumentException> { service.react("@ada", 0, "\uD83D\uDC4D") }
        assertFailsWith<IllegalArgumentException> { service.removeReaction("@ada", -3) }

        assertEquals(emptyList(), gateway.edits)
        assertEquals(emptyList(), gateway.deletes)
        assertEquals(emptyList(), gateway.forwards)
        assertEquals(emptyList(), gateway.pins)
        assertEquals(emptyList(), gateway.unpins)
        assertEquals(emptyList(), gateway.reactions)
        assertEquals(emptyList(), gateway.removedReactions)
    }
}

private data class SendCall(
    val reference: String,
    val text: String,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

private data class EditCall(val reference: String, val messageId: Int, val text: String)

private data class DeleteCall(val reference: String, val messageIds: List<Int>)

private data class ForwardCall(val fromReference: String, val messageIds: List<Int>, val toReference: String)

private data class IdCall(val reference: String, val messageId: Int)

private data class ReactCall(val reference: String, val messageId: Int, val emoji: String)

private data class ChatActionCall(val reference: String, val activity: ChatActivity)

private class FakeMessageWriteGateway : MessageWriteGateway {
    val sends = mutableListOf<SendCall>()
    val edits = mutableListOf<EditCall>()
    val deletes = mutableListOf<DeleteCall>()
    val forwards = mutableListOf<ForwardCall>()
    val pins = mutableListOf<IdCall>()
    val unpins = mutableListOf<IdCall>()
    val reactions = mutableListOf<ReactCall>()
    val removedReactions = mutableListOf<IdCall>()
    val markReads = mutableListOf<String>()
    val chatActions = mutableListOf<ChatActionCall>()

    var sent: Message = message
    var edited: Message = message
    var deleted: Int = 2
    var forwarded: List<Message> = listOf(message)

    override fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message {
        sends += SendCall(reference, text, replyToMessageId, silent)
        return sent
    }

    override fun edit(reference: String, messageId: Int, text: String): Message {
        edits += EditCall(reference, messageId, text)
        return edited
    }

    override fun delete(reference: String, messageIds: List<Int>): Int {
        deletes += DeleteCall(reference, messageIds)
        return deleted
    }

    override fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message> {
        forwards += ForwardCall(fromReference, messageIds, toReference)
        return forwarded
    }

    override fun pin(reference: String, messageId: Int) {
        pins += IdCall(reference, messageId)
    }

    override fun unpin(reference: String, messageId: Int) {
        unpins += IdCall(reference, messageId)
    }

    override fun react(reference: String, messageId: Int, emoji: String) {
        reactions += ReactCall(reference, messageId, emoji)
    }

    override fun removeReaction(reference: String, messageId: Int) {
        removedReactions += IdCall(reference, messageId)
    }

    override fun markRead(reference: String) {
        markReads += reference
    }

    override fun sendChatAction(reference: String, activity: ChatActivity) {
        chatActions += ChatActionCall(reference, activity)
    }
}

private val message = Message(
    id = 1,
    senderName = "Ada",
    text = "hi",
    sentAt = Instant.parse("2026-09-30T10:00:00Z"),
    outgoing = false,
)
