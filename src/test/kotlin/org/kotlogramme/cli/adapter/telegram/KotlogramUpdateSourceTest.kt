package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramUpdate
import org.kotlogramme.cli.domain.IncomingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlogramUpdateSourceTest {
    @Test
    fun `maps a message update to a new message with its chat`() {
        val facadePeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        val facadeMessage = message(id = 42, text = "hi", date = 1_000, peer = facadePeer)
        val operations = FakeUpdateOperations().apply {
            updates += TelegramUpdate(kind = "newMessage", message = facadeMessage)
        }

        val update = KotlogramUpdateSource(operations).next(timeoutMillis = 5_000)

        val newMessage = update as IncomingUpdate.NewMessage
        assertEquals(7, newMessage.chat?.id)
        assertEquals("Ada", newMessage.chat?.title)
        assertEquals(42, newMessage.message.id)
        assertEquals("hi", newMessage.message.text)
        assertEquals(listOf(5_000L), operations.timeouts)
    }

    @Test
    fun `maps a message update without a peer to a null chat`() {
        val operations = FakeUpdateOperations().apply {
            updates += TelegramUpdate(kind = "newMessage", message = message(id = 5, text = "orphan"))
        }

        val update = KotlogramUpdateSource(operations).next(timeoutMillis = 5_000)

        assertNull((update as IncomingUpdate.NewMessage).chat)
        assertEquals(5, update.message.id)
    }

    @Test
    fun `maps a non-message update to its kind`() {
        val operations = FakeUpdateOperations().apply {
            updates += TelegramUpdate(kind = "typing")
        }

        val update = KotlogramUpdateSource(operations).next(timeoutMillis = 5_000)

        assertEquals(IncomingUpdate.Other("typing"), update)
    }

    @Test
    fun `maps a facade timeout to null`() {
        val operations = FakeUpdateOperations()

        val update = KotlogramUpdateSource(operations).next(timeoutMillis = 5_000)

        assertNull(update)
        assertEquals(listOf(5_000L), operations.timeouts)
    }
}

internal class FakeUpdateOperations : FacadeUpdateOperations {
    val updates = ArrayDeque<TelegramUpdate?>()
    val timeouts = mutableListOf<Long>()
    var syncs = 0

    override fun next(timeoutMillis: Long): TelegramUpdate? {
        timeouts += timeoutMillis
        return updates.removeFirstOrNull()
    }

    override fun syncState() {
        syncs++
    }
}
