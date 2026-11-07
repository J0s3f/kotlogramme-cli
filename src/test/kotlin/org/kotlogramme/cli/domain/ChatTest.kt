package org.kotlogramme.cli.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTest {
    private val chat = Chat(
        id = 42,
        title = "Ada Lovelace",
        kind = ChatKind.PRIVATE,
        username = "ada",
        lastMessagePreview = null,
        lastMessageAt = null,
    )

    @Test
    fun `is named by its numeric id`() {
        assertTrue(chat.isNamedBy("42"))
    }

    @Test
    fun `is named by its username with or without the at sign, ignoring case`() {
        assertTrue(chat.isNamedBy("@Ada"))
        assertTrue(chat.isNamedBy("ada"))
    }

    @Test
    fun `is named by its title, ignoring case and surrounding blanks`() {
        assertTrue(chat.isNamedBy("  ada lovelace "))
    }

    @Test
    fun `is not named by another chat's reference`() {
        assertFalse(chat.isNamedBy("43"))
        assertFalse(chat.isNamedBy("@bob"))
    }

    @Test
    fun `a chat without a username is not named by an empty selector`() {
        assertFalse(chat.copy(username = null).isNamedBy("@"))
    }
}
