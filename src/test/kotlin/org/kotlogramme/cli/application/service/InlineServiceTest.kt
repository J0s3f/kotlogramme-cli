package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.InlineGateway
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.InlineResult
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InlineServiceTest {
    @Test
    fun `query passes the valid input through unchanged`() {
        val gateway = FakeInlineGateway()

        val result = InlineService(gateway).query("@gif", "cats", reference = "9")

        assertEquals(InlineQueryCall("@gif", "cats", "9"), gateway.queries.single())
        assertEquals(gateway.answer, result)
    }

    @Test
    fun `send passes the valid input through unchanged`() {
        val gateway = FakeInlineGateway()

        val sent = InlineService(gateway).send("9", 55, "r1")

        assertEquals(InlineSendCall("9", 55, "r1"), gateway.sends.single())
        assertEquals(gateway.message, sent)
    }

    @Test
    fun `rejects a blank query before the gateway`() {
        val gateway = FakeInlineGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            InlineService(gateway).query("@gif", "   ", null)
        }

        assertTrue(error.message.orEmpty().contains("must not be blank"))
        assertEquals(emptyList(), gateway.queries)
    }

    @Test
    fun `rejects a blank bot before the gateway`() {
        val gateway = FakeInlineGateway()

        val error = assertFailsWith<IllegalArgumentException> {
            InlineService(gateway).query("  ", "cats", null)
        }

        assertTrue(error.message.orEmpty().contains("must not be blank"))
        assertEquals(emptyList(), gateway.queries)
    }

    @Test
    fun `rejects a non-positive query id before the gateway`() {
        val gateway = FakeInlineGateway()
        val service = InlineService(gateway)

        assertFailsWith<IllegalArgumentException> { service.send("9", 0, "r1") }
        assertFailsWith<IllegalArgumentException> { service.send("9", -1, "r1") }
        assertEquals(emptyList(), gateway.sends)
    }
}

private data class InlineQueryCall(val bot: String, val query: String, val reference: String?)

private data class InlineSendCall(val reference: String, val queryId: Long, val resultId: String)

private class FakeInlineGateway : InlineGateway {
    var answer: InlineQuery = InlineQuery(
        queryId = 55,
        results = listOf(InlineResult(id = "r1", type = "article", title = "Hi", description = null, text = "hi")),
    )
    var message: Message = Message(
        id = 42,
        senderName = "Ada",
        text = "posted",
        sentAt = Instant.parse("2026-09-30T10:00:00Z"),
        outgoing = true,
    )
    val queries = mutableListOf<InlineQueryCall>()
    val sends = mutableListOf<InlineSendCall>()

    override fun query(bot: String, query: String, reference: String?): InlineQuery {
        queries += InlineQueryCall(bot, query, reference)
        return answer
    }

    override fun send(reference: String, queryId: Long, resultId: String): Message? {
        sends += InlineSendCall(reference, queryId, resultId)
        return message
    }
}
