package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramMessageGatewayTest {
    @Test
    fun `history resolves the reference, forwards the offset and reverses the page to oldest first`() {
        val facadePeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        val chatOperations = FakeChatOperations().apply { resolvedPeer = facadePeer }
        val messageOperations = FakeMessageOperations().apply {
            messages = listOf(
                message(id = 2, text = "newer", date = 2_000),
                message(id = 1, text = "older", date = 1_000),
            )
        }

        val page = KotlogramMessageGateway(chatOperations, messageOperations)
            .history("@ada", limit = 10, beforeMessageId = 5)

        assertEquals(listOf("ada"), chatOperations.resolvedUsernames)
        assertEquals(HistoryCall(facadePeer, 10, 5), messageOperations.calls.single())
        assertEquals(listOf(1, 2), page.map { it.id })
        assertEquals(listOf("older", "newer"), page.map { it.text })
    }
}

internal data class HistoryCall(val peer: TelegramPeer, val limit: Int, val beforeMessageId: Int?)

internal class FakeMessageOperations : FacadeMessageOperations {
    var messages: List<Message> = emptyList()
    val calls = mutableListOf<HistoryCall>()

    override fun history(peer: TelegramPeer, limit: Int, beforeMessageId: Int?): List<Message> {
        calls += HistoryCall(peer, limit, beforeMessageId)
        return messages
    }
}
