package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.Message as FacadeMessage
import com.github.badoualy.telegram.api.StickerSetResult
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KotlogramStickerGatewayTest {
    @Test
    fun `sets asks for the installed sets and maps them in order`() {
        val operations = FakeStickerOperations().apply {
            all = allStickers(listOf(stickerSet(id = 1, shortName = "a"), stickerSet(id = 2, shortName = "b")))
        }

        val sets = gateway(operations).sets()

        assertEquals(1, operations.setListCalls)
        assertEquals(listOf("a", "b"), sets.map { it.shortName })
    }

    @Test
    fun `sets treats the not-modified marker as an empty list`() {
        val operations = FakeStickerOperations().apply { all = allStickers(emptyList(), notModified = true) }

        assertEquals(emptyList(), gateway(operations).sets())
    }

    @Test
    fun `set parses a short name into the short-name shape`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(stickerSet(shortName = "cats")) }

        val set = gateway(operations).set("cats")

        assertEquals(listOf(StickerCall(id = null, accessHash = null, shortName = "cats")), operations.setCalls)
        assertEquals("cats", set.shortName)
    }

    @Test
    fun `set parses id and access hash into the id shape`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(stickerSet()) }

        val set = gateway(operations).set("42:99")

        assertEquals(listOf(StickerCall(id = 42, accessHash = 99, shortName = null)), operations.setCalls)
        assertEquals(42L, set.id)
    }

    @Test
    fun `set rejects a malformed id reference before the facade`() {
        val operations = FakeStickerOperations()

        assertFailsWith<IllegalArgumentException> { gateway(operations).set("abc:def") }
        assertEquals(emptyList(), operations.setCalls)
    }

    @Test
    fun `set raises a clear error for the not-modified marker`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(set = null, notModified = true) }

        assertFailsWith<IllegalStateException> { gateway(operations).set("cats") }
    }

    @Test
    fun `send resolves the chat, parses the set and maps the sent message`() {
        val chat = peer(id = -9, kind = "channel", name = "The Club", megagroup = true)
        val chatOperations = FakeChatOperations().apply { resolvedPeer = chat }
        val operations = FakeStickerOperations().apply { sent = message(id = 7, text = "a sticker") }

        val sent = KotlogramStickerGateway(operations, ChatReferenceResolver(chatOperations))
            .send("@club", "42:99", index = 3, replyToMessageId = 5, silent = true)

        assertEquals(
            listOf(SendStickerCall(chat, id = 42, accessHash = 99, shortName = null, index = 3, replyToMessageId = 5, silent = true)),
            operations.sends,
        )
        assertEquals(7, sent.id)
    }

    private fun gateway(operations: FakeStickerOperations): KotlogramStickerGateway =
        KotlogramStickerGateway(operations, ChatReferenceResolver(FakeChatOperations()))
}

internal data class StickerCall(val id: Long?, val accessHash: Long?, val shortName: String?)

internal data class SendStickerCall(
    val peer: TelegramPeer,
    val id: Long?,
    val accessHash: Long?,
    val shortName: String?,
    val index: Int,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

internal class FakeStickerOperations : FacadeStickerOperations {
    var all: AllStickers = AllStickers(notModified = false, hash = 0, sets = emptyList())
    var result: StickerSetResult = StickerSetResult(notModified = false, set = null)
    var sent: FacadeMessage = message(id = 1)
    var setListCalls = 0
    val setCalls = mutableListOf<StickerCall>()
    val sends = mutableListOf<SendStickerCall>()

    override fun sets(): AllStickers {
        setListCalls += 1
        return all
    }

    override fun set(id: Long?, accessHash: Long?, shortName: String?): StickerSetResult {
        setCalls += StickerCall(id, accessHash, shortName)
        return result
    }

    override fun send(
        peer: TelegramPeer,
        id: Long?,
        accessHash: Long?,
        shortName: String?,
        index: Int,
        replyToMessageId: Int?,
        silent: Boolean,
    ): FacadeMessage {
        sends += SendStickerCall(peer, id, accessHash, shortName, index, replyToMessageId, silent)
        return sent
    }
}
