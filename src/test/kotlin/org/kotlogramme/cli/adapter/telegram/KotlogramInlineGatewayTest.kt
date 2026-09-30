package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.InlineQueryResults
import com.github.badoualy.telegram.api.InlineResult as FacadeInlineResult
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlogramInlineGatewayTest {
    private val bot = peer(id = 7, kind = "user", username = "gif", name = "GIF")
    private val chat = peer(id = -9, kind = "group", name = "The Club")

    @Test
    fun `query resolves the bot and the chat context and maps the answer`() {
        val chatOperations = FakeChatOperations().apply {
            resolvedPeer = bot
            peerById = chat
        }
        val operations = FakeInlineOperations().apply {
            answer = InlineQueryResults(
                queryId = 55,
                results = listOf(
                    FacadeInlineResult(kind = "result", id = "r1", type = "article", title = "Hi"),
                ),
            )
        }

        val query = KotlogramInlineGateway(operations, ChatReferenceResolver(chatOperations))
            .query("@gif", "cats", reference = "9")

        assertEquals(listOf("gif"), chatOperations.resolvedUsernames)
        assertEquals(listOf(9L), chatOperations.resolvedIds)
        assertEquals(QueryCall(bot, "cats", chat), operations.queries.single())
        assertEquals(55, query.queryId)
        assertEquals(listOf("r1"), query.results.map { it.id })
    }

    @Test
    fun `query without a chat context resolves only the bot`() {
        val chatOperations = FakeChatOperations().apply { resolvedPeer = bot }
        val operations = FakeInlineOperations()

        KotlogramInlineGateway(operations, ChatReferenceResolver(chatOperations))
            .query("@gif", "cats", reference = null)

        assertEquals(QueryCall(bot, "cats", null), operations.queries.single())
        assertEquals(emptyList(), chatOperations.resolvedIds)
    }

    @Test
    fun `send resolves the chat and carries the query id of the answered query`() {
        val chatOperations = FakeChatOperations().apply {
            resolvedPeer = bot
            peerById = chat
        }
        val operations = FakeInlineOperations().apply {
            answer = InlineQueryResults(
                queryId = 55,
                results = listOf(
                    FacadeInlineResult(kind = "result", id = "r1", type = "article", title = "Hi"),
                ),
            )
            sentMessage = message(id = 42, text = "posted", date = 1_000, outgoing = true)
        }
        val gateway = KotlogramInlineGateway(operations, ChatReferenceResolver(chatOperations))

        val query = gateway.query("@gif", "cats", reference = null)
        val sent = gateway.send("9", query.queryId, query.results.single().id)

        assertEquals(listOf(9L), chatOperations.resolvedIds)
        assertEquals(SendInlineCall(chat, 55, "r1"), operations.sends.single())
        assertEquals(42, sent?.id)
        assertEquals("posted", sent?.text)
        assertEquals(true, sent?.outgoing)
    }

    @Test
    fun `send returns null when the facade could not name the message`() {
        val chatOperations = FakeChatOperations().apply { peerById = chat }
        val operations = FakeInlineOperations().apply { sentMessage = null }

        val sent = KotlogramInlineGateway(operations, ChatReferenceResolver(chatOperations))
            .send("9", 55, "r1")

        assertEquals(SendInlineCall(chat, 55, "r1"), operations.sends.single())
        assertNull(sent)
    }
}

internal data class QueryCall(val bot: TelegramPeer, val query: String, val peer: TelegramPeer?)

internal data class SendInlineCall(val peer: TelegramPeer, val queryId: Long, val resultId: String)

internal class FakeInlineOperations : FacadeInlineOperations {
    var answer: InlineQueryResults = InlineQueryResults(queryId = 0)
    var sentMessage: Message? = null
    val queries = mutableListOf<QueryCall>()
    val sends = mutableListOf<SendInlineCall>()

    override fun query(bot: TelegramPeer, query: String, peer: TelegramPeer?): InlineQueryResults {
        queries += QueryCall(bot, query, peer)
        return answer
    }

    override fun send(peer: TelegramPeer, queryId: Long, resultId: String): Message? {
        sends += SendInlineCall(peer, queryId, resultId)
        return sentMessage
    }
}
