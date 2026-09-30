package org.kotlogramme.cli.adapter.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatReferenceResolverTest {
    @Test
    fun `a numeric id resolves directly, which is the only path a bot has`() {
        val ada = peer(id = 496447584, kind = "user", username = "ada", name = "Ada")
        val operations = FakeChatOperations().apply { peerById = ada }

        val resolved = ChatReferenceResolver(operations).resolve("496447584")

        assertSame(ada, resolved)
        assertEquals(listOf(496447584L), operations.resolvedIds)
    }

    @Test
    fun `a numeric id falls back to the dialog list when the direct lookup misses`() {
        val ada = peer(id = 5, kind = "user", name = "Ada")
        val operations = FakeChatOperations().apply { dialogs = listOf(dialog(ada)) }

        val resolved = ChatReferenceResolver(operations).resolve("5")

        assertSame(ada, resolved)
        assertEquals(listOf(5L), operations.resolvedIds)
    }

    @Test
    fun `a numeric id neither path knows is refused with a message naming it`() {
        val operations = FakeChatOperations()

        val error = assertFailsWith<IllegalArgumentException> {
            ChatReferenceResolver(operations).resolve("42")
        }

        assertTrue(error.message!!.contains("42"))
    }

    @Test
    fun `a username takes the username path and never the id paths`() {
        val ada = peer(id = 1, kind = "user", username = "ada", name = "Ada")
        val operations = FakeChatOperations().apply { resolvedPeer = ada }

        assertSame(ada, ChatReferenceResolver(operations).resolve("@ada"))
        assertEquals(listOf("ada"), operations.resolvedUsernames)
        assertEquals(emptyList(), operations.resolvedIds)
    }
}
