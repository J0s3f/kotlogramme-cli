package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.MessageGateway
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadHistoryServiceTest {
    @Test
    fun `reads through the gateway with the reference, limit and offset`() {
        val gateway = FakeMessageGateway().apply { messages = listOf(message) }

        val result = ReadHistoryService(gateway).read("@ada", limit = 20, beforeMessageId = 7)

        assertEquals(ReadCall("@ada", 20, 7), gateway.calls.single())
        assertEquals(listOf(message), result)
    }

    @Test
    fun `rejects a non-positive limit`() {
        val gateway = FakeMessageGateway()

        assertFailsWith<IllegalArgumentException> { ReadHistoryService(gateway).read("@ada", 0, null) }
        assertFailsWith<IllegalArgumentException> { ReadHistoryService(gateway).read("@ada", -5, null) }
        assertEquals(emptyList(), gateway.calls)
    }

    private class FakeMessageGateway : MessageGateway {
        val calls = mutableListOf<ReadCall>()
        var messages: List<Message> = emptyList()

        override fun history(reference: String, limit: Int, beforeMessageId: Int?): List<Message> {
            calls += ReadCall(reference, limit, beforeMessageId)
            return messages
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
