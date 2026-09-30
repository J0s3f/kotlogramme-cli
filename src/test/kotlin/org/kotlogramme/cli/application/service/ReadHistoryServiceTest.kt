package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.MessageGateway
import org.kotlogramme.cli.application.port.spi.UserGateway
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReadHistoryServiceTest {
    @Test
    fun `reads through the gateway with the reference, limit and offset`() {
        val gateway = FakeMessageGateway().apply { messages = listOf(message) }

        val result = ReadHistoryService(gateway, FakeUserGateway()).read("@ada", limit = 20, beforeMessageId = 7)

        assertEquals(ReadCall("@ada", 20, 7), gateway.calls.single())
        assertEquals(listOf(message), result)
    }

    @Test
    fun `rejects a non-positive limit`() {
        val gateway = FakeMessageGateway()

        assertFailsWith<IllegalArgumentException> {
            ReadHistoryService(gateway, FakeUserGateway()).read("@ada", 0, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ReadHistoryService(gateway, FakeUserGateway()).read("@ada", -5, null)
        }
        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun `resolves the distinct via bot ids in one batched lookup`() {
        val users = FakeUserGateway().apply {
            usernames = mapOf(99L to "inline_bot", 100L to "other_bot")
        }
        val gateway = FakeMessageGateway().apply {
            messages = listOf(
                message.copy(id = 1, viaBotId = 99),
                message.copy(id = 2, viaBotId = 99),
                message.copy(id = 3, viaBotId = 100),
                message.copy(id = 4),
            )
        }

        val result = ReadHistoryService(gateway, users).read("@ada", limit = 20, beforeMessageId = null)

        assertEquals(listOf(listOf(99L, 100L)), users.lookups)
        assertEquals(listOf("inline_bot", "inline_bot", "other_bot", null), result.map { it.viaBotUsername })
    }

    @Test
    fun `an unresolved id keeps the message and leaves the username absent`() {
        val users = FakeUserGateway().apply { usernames = mapOf(99L to "inline_bot") }
        val gateway = FakeMessageGateway().apply {
            messages = listOf(message.copy(id = 1, viaBotId = 99), message.copy(id = 2, viaBotId = 100))
        }

        val result = ReadHistoryService(gateway, users).read("@ada", limit = 20, beforeMessageId = null)

        assertEquals("inline_bot", result[0].viaBotUsername)
        assertNull(result[1].viaBotUsername)
        assertEquals(100L, result[1].viaBotId)
    }

    @Test
    fun `a failing lookup does not fail the read`() {
        val users = FakeUserGateway().apply { failure = IllegalStateException("no such user") }
        val gateway = FakeMessageGateway().apply {
            messages = listOf(message.copy(id = 1, viaBotId = 99))
        }

        val result = ReadHistoryService(gateway, users).read("@ada", limit = 20, beforeMessageId = null)

        assertEquals(listOf(1), result.map { it.id })
        assertNull(result.single().viaBotUsername)
    }

    @Test
    fun `a page without a via bot does not look anything up`() {
        val users = FakeUserGateway()

        ReadHistoryService(FakeMessageGateway().apply { messages = listOf(message) }, users)
            .read("@ada", limit = 20, beforeMessageId = null)

        assertEquals(emptyList(), users.lookups)
    }

    private class FakeMessageGateway : MessageGateway {
        val calls = mutableListOf<ReadCall>()
        var messages: List<Message> = emptyList()

        override fun history(reference: String, limit: Int, beforeMessageId: Int?): List<Message> {
            calls += ReadCall(reference, limit, beforeMessageId)
            return messages
        }
    }

    private class FakeUserGateway : UserGateway {
        var usernames: Map<Long, String> = emptyMap()
        var failure: Exception? = null
        val lookups = mutableListOf<List<Long>>()

        override fun usernames(ids: List<Long>): Map<Long, String> {
            lookups += ids
            failure?.let { throw it }
            return usernames.filterKeys { it in ids }
        }
    }

    private data class ReadCall(val reference: String, val limit: Int, val beforeMessageId: Int?)

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
