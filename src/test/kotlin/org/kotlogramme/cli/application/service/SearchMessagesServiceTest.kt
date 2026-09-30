package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchMessagesServiceTest {
    @Test
    fun `per-chat search passes the reference, query and limit through`() {
        val gateway = FakeMessageSearchGateway().apply { results = listOf(message) }

        val result = SearchMessagesService(gateway).search("@ada", "hi", limit = 10)

        assertEquals(listOf(SearchCall("@ada", "hi", 10)), gateway.searches)
        assertEquals(listOf(message), result)
    }

    @Test
    fun `global search passes a null reference through`() {
        val gateway = FakeMessageSearchGateway().apply { results = listOf(message) }

        val result = SearchMessagesService(gateway).search(reference = null, query = "hi", limit = 5)

        assertEquals(listOf(SearchCall(null, "hi", 5)), gateway.searches)
        assertEquals(listOf(message), result)
    }

    @Test
    fun `total passes the reference and query through`() {
        val gateway = FakeMessageSearchGateway().apply { totalCount = 3 }

        val total = SearchMessagesService(gateway).total("@ada", "hi")

        assertEquals(listOf(TotalCall("@ada", "hi")), gateway.totals)
        assertEquals(3, total)
    }

    @Test
    fun `rejects a blank query before the gateway`() {
        val gateway = FakeMessageSearchGateway()
        val service = SearchMessagesService(gateway)

        assertFailsWith<IllegalArgumentException> { service.search("@ada", "", 10) }
        assertFailsWith<IllegalArgumentException> { service.search("@ada", "   ", 10) }
        assertFailsWith<IllegalArgumentException> { service.total("@ada", "") }
        assertEquals(emptyList(), gateway.searches)
        assertEquals(emptyList(), gateway.totals)
    }

    @Test
    fun `rejects a non-positive limit before the gateway`() {
        val gateway = FakeMessageSearchGateway()
        val service = SearchMessagesService(gateway)

        assertFailsWith<IllegalArgumentException> { service.search("@ada", "hi", 0) }
        assertFailsWith<IllegalArgumentException> { service.search("@ada", "hi", -1) }
        assertEquals(emptyList(), gateway.searches)
    }

    private class FakeMessageSearchGateway : MessageSearchGateway {
        var results: List<Message> = emptyList()
        var totalCount: Int = 0
        val searches = mutableListOf<SearchCall>()
        val totals = mutableListOf<TotalCall>()

        override fun search(reference: String?, query: String, limit: Int): List<Message> {
            searches += SearchCall(reference, query, limit)
            return results
        }

        override fun total(reference: String?, query: String): Int {
            totals += TotalCall(reference, query)
            return totalCount
        }
    }

    private data class SearchCall(val reference: String?, val query: String, val limit: Int)

    private data class TotalCall(val reference: String?, val query: String)

    private companion object {
        val message = Message(
            id = 1,
            senderName = "Ada",
            text = "hi",
            sentAt = Instant.parse("2026-09-30T10:00:00Z"),
            outgoing = false,
        )
    }
}
