package org.kotlogramme.cli.application

import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ListingCursorTest {
    @Test
    fun `decimal parses a message-id or offset cursor`() {
        assertEquals(42, ListingCursor.parseDecimal("42"))
    }

    @Test
    fun `decimal rejects a malformed cursor`() {
        assertFailsWith<IllegalArgumentException> { ListingCursor.parseDecimal("abc") }
        assertFailsWith<IllegalArgumentException> { ListingCursor.parseDecimal("1:2") }
    }

    @Test
    fun `dialogs parses a cursor into the offset triple`() {
        val cursor = ListingCursor.parseDialogs("7:5:1000")

        assertEquals(7L, cursor.peerId)
        assertEquals(5, cursor.topMessageId)
        assertEquals(1000L, cursor.dateMillis)
    }

    @Test
    fun `dialogs rejects a malformed cursor`() {
        assertFailsWith<IllegalArgumentException> { ListingCursor.parseDialogs("7:5") }
        assertFailsWith<IllegalArgumentException> { ListingCursor.parseDialogs("7:5:1000:extra") }
        assertFailsWith<IllegalArgumentException> { ListingCursor.parseDialogs("a:b:c") }
    }

    @Test
    fun `dialogs formats the cursor from a chat`() {
        val chat = Chat(
            id = 7,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = null,
            lastMessageAt = Instant.ofEpochMilli(1000),
            topMessageId = 5,
        )

        assertEquals("7:5:1000", ListingCursor.formatDialogs(chat))
    }

    @Test
    fun `dialogs formats a zero date when the chat has no last message`() {
        val chat = Chat(
            id = 7,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = null,
            lastMessageAt = null,
            topMessageId = 5,
        )

        assertEquals("7:5:0", ListingCursor.formatDialogs(chat))
    }
}
