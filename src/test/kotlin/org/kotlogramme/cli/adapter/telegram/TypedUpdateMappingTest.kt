package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TypedUpdate
import org.kotlogramme.cli.domain.IncomingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypedUpdateMappingTest {
    @Test
    fun `maps a message update to a new message with its chat`() {
        val facadePeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        val facadeMessage = message(id = 42, text = "hi", date = 1_000, peer = facadePeer)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage).toIncomingUpdate()

        val newMessage = update as IncomingUpdate.NewMessage
        assertEquals(7, newMessage.chat?.id)
        assertEquals("Ada", newMessage.chat?.title)
        assertEquals(42, newMessage.message.id)
        assertEquals("hi", newMessage.message.text)
    }

    @Test
    fun `maps a message update without a peer to a null chat`() {
        val update = TypedUpdate(kind = "newMessage", message = message(id = 5, text = "orphan")).toIncomingUpdate()

        assertNull((update as IncomingUpdate.NewMessage).chat)
        assertEquals(5, update.message.id)
    }

    @Test
    fun `maps a non-message update to its kind`() {
        val update = TypedUpdate(kind = "typing").toIncomingUpdate()

        assertEquals(IncomingUpdate.Other("typing"), update)
    }
}
