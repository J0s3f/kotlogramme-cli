package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.MessageSearchFilter
import com.github.badoualy.telegram.api.TelegramPeer
import org.kotlogramme.cli.domain.MediaFileKind
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramMessageSearchGatewayTest {
    private val ada = peer(id = 7, kind = "user", username = "ada", name = "Ada")

    @Test
    fun `per-chat search resolves the reference and asks the peer search`() {
        val operations = FakeSearchOperations().apply {
            messages = listOf(
                message(id = 2, text = "newer", date = 2_000),
                message(id = 1, text = "older", date = 1_000),
            )
        }

        val results = gatewayWith(operations).search("@ada", "hi", limit = 10)

        assertEquals(listOf(PeerSearchCall(ada, "hi", 10)), operations.searches)
        assertEquals(emptyList(), operations.globalSearches)
        assertEquals(listOf(2, 1), results.map { it.id })
        assertEquals(listOf("newer", "older"), results.map { it.text })
    }

    @Test
    fun `a null reference asks the global search and skips resolution`() {
        val operations = FakeSearchOperations().apply {
            globalMessages = listOf(message(id = 9, text = "everywhere", date = 3_000))
        }

        val results = gatewayWith(operations).search(reference = null, query = "hi", limit = 5)

        assertEquals(listOf(GlobalSearchCall("hi", 5)), operations.globalSearches)
        assertEquals(emptyList(), operations.searches)
        assertEquals(listOf("everywhere"), results.map { it.text })
    }

    @Test
    fun `per-chat total resolves the reference and asks the peer total`() {
        val operations = FakeSearchOperations().apply { totalCount = 42 }

        val total = gatewayWith(operations).total("@ada", "hi")

        assertEquals(listOf(PeerTotalCall(ada, "hi")), operations.totals)
        assertEquals(emptyList(), operations.globalTotals)
        assertEquals(42, total)
    }

    @Test
    fun `a null reference asks the global total and skips resolution`() {
        val operations = FakeSearchOperations().apply { globalTotalCount = 7 }

        val total = gatewayWith(operations).total(reference = null, query = "hi")

        assertEquals(listOf("hi"), operations.globalTotals)
        assertEquals(emptyList(), operations.totals)
        assertEquals(7, total)
    }

    @Test
    fun `files resolves the reference and asks for the kind's filter`() {
        val operations = FakeSearchOperations().apply {
            fileMessages = listOf(
                message(id = 3, text = "", date = 3_000, media = com.github.badoualy.telegram.api.Media("video")),
                message(id = 2, text = "", date = 2_000, media = com.github.badoualy.telegram.api.Media("video")),
            )
        }

        val results = gatewayWith(operations).files("@ada", MediaFileKind.VIDEO, limit = 10)

        assertEquals(listOf(FileSearchCall(ada, MessageSearchFilter.VIDEO, 10)), operations.fileSearches)
        assertEquals(listOf(3, 2), results.map { it.id })
    }

    @Test
    fun `an animation asks for the gif filter, as Telegram lists animations there`() {
        val operations = FakeSearchOperations()

        gatewayWith(operations).files("@ada", MediaFileKind.ANIMATION, limit = 5)

        assertEquals(listOf(FileSearchCall(ada, MessageSearchFilter.GIF, 5)), operations.fileSearches)
    }

    @Test
    fun `fileTotal resolves the reference and asks for the kind's filter`() {
        val operations = FakeSearchOperations().apply { fileTotalCount = 4 }

        val total = gatewayWith(operations).fileTotal("@ada", MediaFileKind.DOCUMENT)

        assertEquals(listOf(FileTotalCall(ada, MessageSearchFilter.DOCUMENT)), operations.fileTotals)
        assertEquals(4, total)
    }

    @Test
    fun `search parses the cursor into the facade offsetId`() {
        val operations = FakeSearchOperations().apply {
            messages = listOf(message(id = 2, text = "newer", date = 2_000))
        }

        gatewayWith(operations).search("@ada", "hi", limit = 10, cursor = "99")

        assertEquals(listOf<Int?>(99), operations.searchOffsets)
    }

    @Test
    fun `files parses the cursor into the facade offsetId`() {
        val operations = FakeSearchOperations().apply {
            fileMessages = listOf(
                message(id = 3, text = "", date = 3_000, media = com.github.badoualy.telegram.api.Media("video")),
            )
        }

        gatewayWith(operations).files("@ada", MediaFileKind.VIDEO, limit = 10, cursor = "99")

        assertEquals(listOf<Int?>(99), operations.fileSearchOffsets)
    }

    private fun gatewayWith(operations: FakeSearchOperations): KotlogramMessageSearchGateway =
        KotlogramMessageSearchGateway(
            operations,
            ChatReferenceResolver(FakeChatOperations().apply { resolvedPeer = ada }),
        )
}

internal data class PeerSearchCall(val peer: TelegramPeer, val query: String, val limit: Int)

internal data class PeerTotalCall(val peer: TelegramPeer, val query: String)

internal data class GlobalSearchCall(val query: String, val limit: Int)

internal data class FileSearchCall(val peer: TelegramPeer, val filter: MessageSearchFilter, val limit: Int)

internal data class FileTotalCall(val peer: TelegramPeer, val filter: MessageSearchFilter)

internal class FakeSearchOperations : FacadeSearchOperations {
    var messages: List<Message> = emptyList()
    var totalCount: Int = 0
    var globalMessages: List<Message> = emptyList()
    var globalTotalCount: Int = 0
    var fileMessages: List<Message> = emptyList()
    var fileTotalCount: Int = 0
    val searches = mutableListOf<PeerSearchCall>()
    val searchOffsets = mutableListOf<Int?>()
    val totals = mutableListOf<PeerTotalCall>()
    val globalSearches = mutableListOf<GlobalSearchCall>()
    val globalSearchOffsets = mutableListOf<Int?>()
    val globalTotals = mutableListOf<String>()
    val fileSearches = mutableListOf<FileSearchCall>()
    val fileSearchOffsets = mutableListOf<Int?>()
    val fileTotals = mutableListOf<FileTotalCall>()

    override fun search(peer: TelegramPeer, query: String, limit: Int, offsetId: Int?): List<Message> {
        searches += PeerSearchCall(peer, query, limit)
        searchOffsets += offsetId
        return messages
    }

    override fun total(peer: TelegramPeer, query: String): Int {
        totals += PeerTotalCall(peer, query)
        return totalCount
    }

    override fun searchGlobal(query: String, limit: Int, offsetId: Int?): List<Message> {
        globalSearches += GlobalSearchCall(query, limit)
        globalSearchOffsets += offsetId
        return globalMessages
    }

    override fun totalGlobal(query: String): Int {
        globalTotals += query
        return globalTotalCount
    }

    override fun searchFiles(peer: TelegramPeer, filter: MessageSearchFilter, limit: Int, offsetId: Int?): List<Message> {
        fileSearches += FileSearchCall(peer, filter, limit)
        fileSearchOffsets += offsetId
        return fileMessages
    }

    override fun totalFiles(peer: TelegramPeer, filter: MessageSearchFilter): Int {
        fileTotals += FileTotalCall(peer, filter)
        return fileTotalCount
    }
}
