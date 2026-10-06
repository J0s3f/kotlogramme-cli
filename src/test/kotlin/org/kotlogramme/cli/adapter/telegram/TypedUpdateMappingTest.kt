package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
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

    @Test
    fun `names a raw update after the Telegram update it carries`() {
        val raw = RawUpdate("updateReadHistoryInbox", byteArrayOf())

        val update = TypedUpdate(kind = "raw", rawUpdate = raw).toIncomingUpdate()

        assertEquals(IncomingUpdate.Other("updateReadHistoryInbox", """{"undecoded":""}"""), update)
    }

    @Test
    fun `carries the decoded payload of a raw update`() {
        val payload = java.util.HexFormat.of().parseHex("def8bde5b50f5911000000004939b9edec07c56a")
        val status = RawUpdate("updateUserStatus", payload)

        val update = TypedUpdate(kind = "raw", rawUpdate = status).toIncomingUpdate() as IncomingUpdate.Other

        assertEquals("updateUserStatus", update.kind)
        assertEquals(true, update.data.contains("\"user_id\":291049397"), update.data)
    }

    @Test
    fun `prefers the resolved peer over the peer id`() {
        val facadeMessage = message(id = 9, text = "hi", peer = peer(id = 7, name = "Ada"), peerId = 4711)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage).toIncomingUpdate()

        assertEquals("Ada", (update as IncomingUpdate.NewMessage).chat?.title)
    }

    @Test
    fun `prefers the resolved sender over the sender id`() {
        val facadeMessage = message(id = 9, text = "hi", sender = user(id = 3, firstName = "Ada"), senderId = 815)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage).toIncomingUpdate()

        assertEquals("Ada", (update as IncomingUpdate.NewMessage).message.senderName)
    }

    @Test
    fun `names the chat of an update that carries only a peer id`() {
        val facadeMessage = message(id = 9, text = "hi", peerId = 4711)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage)
            .toIncomingUpdate(names = { id -> "Ada".takeIf { id == 4711L } })

        assertEquals("Ada", (update as IncomingUpdate.NewMessage).chat?.title)
    }

    @Test
    fun `names the sender of an update that carries only a sender id`() {
        val facadeMessage = message(id = 9, text = "hi", senderId = 815)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage)
            .toIncomingUpdate(names = { id -> "Joe".takeIf { id == 815L } })

        assertEquals("Joe", (update as IncomingUpdate.NewMessage).message.senderName)
    }

    @Test
    fun `keeps the id when the name cannot be resolved`() {
        val facadeMessage = message(id = 9, text = "hi", peerId = 4711, senderId = 815)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage)
            .toIncomingUpdate(names = { null }) as IncomingUpdate.NewMessage

        assertEquals("4711", update.chat?.title)
        assertEquals("815", update.message.senderName)
    }

    @Test
    fun `does not look up a name the update already carries`() {
        val facadeMessage = message(
            id = 9,
            text = "hi",
            peer = peer(id = 7, name = "Ada"),
            peerId = 7,
            sender = user(id = 3, firstName = "Bob"),
            senderId = 3,
        )

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage)
            .toIncomingUpdate(names = { error("looked up $it") }) as IncomingUpdate.NewMessage

        assertEquals("Ada", update.chat?.title)
        assertEquals("Bob", update.message.senderName)
    }

    private fun newMessage(
        update: TypedUpdate,
        names: (Long) -> String? = { null },
        selfId: () -> Long? = { null },
    ) = update.toIncomingUpdate(names, selfId) as IncomingUpdate.NewMessage

    @Test
    fun `a message you sent that names no sender is from you`() {
        val facadeMessage = message(id = 9, text = "hi", outgoing = true, peer = peer(id = 7, name = "Ada"))

        val update = newMessage(
            TypedUpdate(kind = "newMessage", message = facadeMessage),
            names = { id -> "Joe".takeIf { id == 815L } },
            selfId = { 815 },
        )

        assertEquals("Joe", update.message.senderName)
        assertEquals(815L, update.message.senderId)
    }

    @Test
    fun `a message you sent shows your id when your name cannot be resolved`() {
        val facadeMessage = message(id = 9, outgoing = true, peer = peer(id = 7, name = "Ada"))

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage), selfId = { 815 })

        assertEquals("815", update.message.senderName)
    }

    @Test
    fun `a message you sent stays unattributed while your id is unknown`() {
        val facadeMessage = message(id = 9, outgoing = true, peer = peer(id = 7, name = "Ada"))

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage), selfId = { null })

        assertEquals("", update.message.senderName)
        assertNull(update.message.senderId)
    }

    @Test
    fun `an incoming message never asks who you are`() {
        val facadeMessage = message(id = 9, peer = peer(id = 7, name = "Ada"))

        val update = newMessage(
            TypedUpdate(kind = "newMessage", message = facadeMessage),
            selfId = { error("asked for the account of an incoming message") },
        )

        assertEquals("Ada", update.message.senderName)
    }

    @Test
    fun `a message whose sender is named does not ask who you are`() {
        val facadeMessage = message(id = 9, outgoing = true, sender = user(id = 815, firstName = "Joe"))

        val update = newMessage(
            TypedUpdate(kind = "newMessage", message = facadeMessage),
            selfId = { error("asked although the update names the sender") },
        )

        assertEquals("Joe", update.message.senderName)
    }

    @Test
    fun `an incoming message in a private chat is from the person the chat is with`() {
        val facadeMessage = message(id = 9, text = "hi", peer = peer(id = 7, name = "Ada"))

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage))

        assertEquals("Ada", update.message.senderName)
        assertEquals(7L, update.message.senderId)
    }

    @Test
    fun `a chat known only by id still names the person who wrote in it`() {
        val facadeMessage = message(id = 9, text = "hi", peerId = 4711)

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage), names = { "Ada Lovelace" })

        assertEquals("Ada Lovelace", update.chat?.title)
        assertEquals("Ada Lovelace", update.message.senderName)
    }

    @Test
    fun `one person has one name within their private chat`() {
        val partner = peer(id = 7, name = "Beatrix")
        val profile = user(id = 7, firstName = "Beatrix", lastName = "Masser")
        val facadeMessage = message(id = 9, peer = partner, sender = profile)

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage))

        assertEquals("Beatrix", update.chat?.title)
        assertEquals("Beatrix", update.message.senderName)
    }

    @Test
    fun `a member of a group keeps their own name`() {
        val group = peer(id = 9, kind = "chat", name = "Club")
        val facadeMessage = message(id = 9, peer = group, sender = user(id = 3, firstName = "Bob"))

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage))

        assertEquals("Bob", update.message.senderName)
        assertEquals(3L, update.message.senderId)
    }

    @Test
    fun `a message you sent in a private chat is not from the person you wrote to`() {
        val facadeMessage = message(id = 9, outgoing = true, peer = peer(id = 7, name = "Ada"), senderId = 815)

        val update = newMessage(TypedUpdate(kind = "newMessage", message = facadeMessage), names = { "Joe" })

        assertEquals("Joe", update.message.senderName)
        assertEquals(815L, update.message.senderId)
    }

    @Test
    fun `an update about a user carries the id of that user`() {
        val payload = java.util.HexFormat.of().parseHex("def8bde5b50f5911000000004939b9edec07c56a")
        val status = TypedUpdate(kind = "raw", rawUpdate = RawUpdate("updateUserStatus", payload))

        assertEquals(291049397L, (status.toIncomingUpdate() as IncomingUpdate.Other).userId)
    }

    @Test
    fun `falls back to the peer id when the update carries no peer`() {
        val facadeMessage = message(id = 9, text = "hi", peerId = 4711)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage).toIncomingUpdate()

        val chat = (update as IncomingUpdate.NewMessage).chat
        assertEquals(4711, chat?.id)
        assertEquals("4711", chat?.title)
    }

    @Test
    fun `falls back to the sender id when the update carries no sender`() {
        val facadeMessage = message(id = 9, text = "hi", senderId = 815)

        val update = TypedUpdate(kind = "newMessage", message = facadeMessage).toIncomingUpdate()

        assertEquals("815", (update as IncomingUpdate.NewMessage).message.senderName)
    }
}
