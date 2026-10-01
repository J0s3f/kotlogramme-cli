package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.MessageSearchGateway
import org.kotlogramme.cli.domain.MediaFileKind
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

    @Test
    fun `files passes the reference, kind and limit through`() {
        val gateway = FakeMessageSearchGateway().apply { fileResults = listOf(message) }

        val result = SearchMessagesService(gateway).files("@ada", MediaFileKind.VIDEO, limit = 15)

        assertEquals(listOf(FileSearchCall("@ada", MediaFileKind.VIDEO, 15)), gateway.fileSearches)
        assertEquals(listOf(message), result)
    }

    @Test
    fun `fileTotal passes the reference and kind through`() {
        val gateway = FakeMessageSearchGateway().apply { fileTotalCount = 9 }

        val total = SearchMessagesService(gateway).fileTotal("@ada", MediaFileKind.DOCUMENT)

        assertEquals(listOf(FileTotalCall("@ada", MediaFileKind.DOCUMENT)), gateway.fileTotals)
        assertEquals(9, total)
    }

    @Test
    fun `files rejects a non-positive limit before the gateway`() {
        val gateway = FakeMessageSearchGateway()
        val service = SearchMessagesService(gateway)

        assertFailsWith<IllegalArgumentException> { service.files("@ada", MediaFileKind.GIF, 0) }
        assertEquals(emptyList(), gateway.fileSearches)
    }

    private class FakeMessageSearchGateway : MessageSearchGateway {
        var results: List<Message> = emptyList()
        var totalCount: Int = 0
        var fileResults: List<Message> = emptyList()
        var fileTotalCount: Int = 0
        val searches = mutableListOf<SearchCall>()
        val totals = mutableListOf<TotalCall>()
        val fileSearches = mutableListOf<FileSearchCall>()
        val fileTotals = mutableListOf<FileTotalCall>()

        override fun search(reference: String?, query: String, limit: Int): List<Message> {
            searches += SearchCall(reference, query, limit)
            return results
        }

        override fun total(reference: String?, query: String): Int {
            totals += TotalCall(reference, query)
            return totalCount
        }

        override fun files(reference: String, kind: MediaFileKind, limit: Int): List<Message> {
            fileSearches += FileSearchCall(reference, kind, limit)
            return fileResults
        }

        override fun fileTotal(reference: String, kind: MediaFileKind): Int {
            fileTotals += FileTotalCall(reference, kind)
            return fileTotalCount
        }
    }

    private data class SearchCall(val reference: String?, val query: String, val limit: Int)

    private data class TotalCall(val reference: String?, val query: String)

    private data class FileSearchCall(val reference: String, val kind: MediaFileKind, val limit: Int)

    private data class FileTotalCall(val reference: String, val kind: MediaFileKind)

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
