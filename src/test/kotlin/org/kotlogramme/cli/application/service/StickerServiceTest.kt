package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.StickerGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.StickerSet
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StickerServiceTest {
    @Test
    fun `sets reads through the gateway`() {
        val gateway = FakeStickerGateway().apply { stored = listOf(aSet) }

        val result = StickerService(gateway).sets()

        assertEquals(1, gateway.setListCalls)
        assertEquals(listOf(aSet), result)
    }

    @Test
    fun `set passes the reference through`() {
        val gateway = FakeStickerGateway().apply { single = aSet }

        val result = StickerService(gateway).set("cats")

        assertEquals(listOf("cats"), gateway.references)
        assertEquals(aSet, result)
    }

    @Test
    fun `rejects a blank reference before the gateway`() {
        val gateway = FakeStickerGateway()
        val service = StickerService(gateway)

        assertFailsWith<IllegalArgumentException> { service.set("") }
        assertFailsWith<IllegalArgumentException> { service.set("   ") }
        assertEquals(emptyList(), gateway.references)
    }

    @Test
    fun `send passes the chat, set and index through`() {
        val gateway = FakeStickerGateway()

        val result = StickerService(gateway).send("@club", "cats", index = 2, replyToMessageId = 7, silent = true)

        assertEquals(listOf(SendCall("@club", "cats", 2, 7, true)), gateway.sends)
        assertEquals(gateway.sent, result)
    }

    @Test
    fun `send rejects a blank chat, a blank set and a negative index before the gateway`() {
        val gateway = FakeStickerGateway()
        val service = StickerService(gateway)

        assertFailsWith<IllegalArgumentException> { service.send("", "cats", 0, null, false) }
        assertFailsWith<IllegalArgumentException> { service.send("@club", "  ", 0, null, false) }
        assertFailsWith<IllegalArgumentException> { service.send("@club", "cats", -1, null, false) }
        assertEquals(emptyList(), gateway.sends)
    }

    private class FakeStickerGateway : StickerGateway {
        var stored: List<StickerSet> = emptyList()
        var single: StickerSet? = null
        var setListCalls = 0
        var sent: Message = Message(
            id = 1,
            senderName = "",
            text = "",
            sentAt = Instant.EPOCH,
            outgoing = true,
        )
        val references = mutableListOf<String>()
        val sends = mutableListOf<SendCall>()

        override fun sets(): List<StickerSet> {
            setListCalls += 1
            return stored
        }

        override fun set(reference: String): StickerSet {
            references += reference
            return single ?: error("no set configured")
        }

        override fun send(
            chatReference: String,
            setReference: String,
            index: Int,
            replyToMessageId: Int?,
            silent: Boolean,
        ): Message {
            sends += SendCall(chatReference, setReference, index, replyToMessageId, silent)
            return sent
        }
    }

    private data class SendCall(
        val chat: String,
        val set: String,
        val index: Int,
        val replyTo: Int?,
        val silent: Boolean,
    )

    private companion object {
        val aSet = StickerSet(id = 42, accessHash = 99, title = "Cats", shortName = "cats", count = 1)
    }
}
