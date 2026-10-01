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

    @Test
    fun `me resolves to the self peer`() {
        val self = peer(id = 99, kind = "user", name = "Sam Self")
        val operations = FakeChatOperations().apply { selfPeer = self }

        assertSame(self, ChatReferenceResolver(operations).resolve("me"))
        assertEquals(1, operations.selfResolutions)
        // The self alias is not a username and must never reach the username path.
        assertEquals(emptyList(), operations.resolvedUsernames)
    }

    @Test
    fun `the at-prefixed me resolves to the self peer without touching the username path`() {
        val self = peer(id = 99, kind = "user", name = "Sam Self")
        val operations = FakeChatOperations().apply { selfPeer = self }

        assertSame(self, ChatReferenceResolver(operations).resolve("@me"))
        assertEquals(1, operations.selfResolutions)
        assertEquals(emptyList(), operations.resolvedUsernames)
    }

    @Test
    fun `the self alias is case-insensitive`() {
        for (reference in listOf("me", "Me", "ME", "@me", "@Me", "@ME")) {
            val self = peer(id = 99, kind = "user", name = "Sam Self")
            val operations = FakeChatOperations().apply { selfPeer = self }

            assertSame(self, ChatReferenceResolver(operations).resolve(reference), reference)
            assertEquals(1, operations.selfResolutions, reference)
        }
    }

    @Test
    fun `a username that merely starts with me is still a username`() {
        val mel = peer(id = 5, kind = "user", username = "mel", name = "Mel")
        val operations = FakeChatOperations().apply { resolvedPeer = mel }

        assertSame(mel, ChatReferenceResolver(operations).resolve("@mel"))
        assertEquals(listOf("mel"), operations.resolvedUsernames)
        assertEquals(0, operations.selfResolutions)
    }
}
