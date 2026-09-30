package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.ChatGateway
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ListDialogsServiceTest {
    @Test
    fun `lists through the gateway with the requested limit`() {
        val gateway = FakeChatGateway().apply { chats = listOf(chat) }

        val result = ListDialogsService(gateway).list(10)

        assertEquals(listOf(10), gateway.limits)
        assertEquals(listOf(chat), result)
    }

    @Test
    fun `rejects a non-positive limit`() {
        val gateway = FakeChatGateway()

        assertFailsWith<IllegalArgumentException> { ListDialogsService(gateway).list(0) }
        assertFailsWith<IllegalArgumentException> { ListDialogsService(gateway).list(-1) }
        assertEquals(emptyList(), gateway.limits)
    }

    private class FakeChatGateway : ChatGateway {
        val limits = mutableListOf<Int>()
        var chats: List<Chat> = emptyList()

        override fun dialogs(limit: Int): List<Chat> {
            limits += limit
            return chats
        }

        override fun resolve(reference: String): Chat = error("not used")
    }

    private companion object {
        val chat = Chat(
            id = 7,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = "hi",
            lastMessageAt = null,
        )
    }
}
