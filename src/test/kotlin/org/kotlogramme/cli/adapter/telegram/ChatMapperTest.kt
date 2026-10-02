package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Media
import org.kotlogramme.cli.domain.ChatKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMapperTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun `maps a private dialog`() {
        val chat = dialog(
            peer = peer(id = 7, kind = "user", username = "ada", name = "Ada Lovelace"),
            unreadCount = 3,
        ).toChat(now)

        assertEquals(7L, chat.id)
        assertEquals("Ada Lovelace", chat.title)
        assertEquals(ChatKind.PRIVATE, chat.kind)
        assertEquals("ada", chat.username)
        assertEquals("@ada", chat.reference)
        assertEquals(3, chat.unreadCount)
    }

    @Test
    fun `maps the dialog's top message id`() {
        val chat = dialog(peer(id = 7, kind = "user", name = "Ada"), topMessage = 42).toChat(now)

        assertEquals(42, chat.topMessageId)
    }

    @Test
    fun `maps a small group`() {
        val chat = dialog(peer(id = -9, kind = "group", name = "The Club")).toChat(now)

        assertEquals(ChatKind.GROUP, chat.kind)
    }

    @Test
    fun `maps a broadcast channel`() {
        val chat = dialog(peer(id = -1009, kind = "channel", name = "News")).toChat(now)

        assertEquals(ChatKind.CHANNEL, chat.kind)
    }

    @Test
    fun `maps a megagroup channel as a supergroup`() {
        val chat = dialog(peer(id = -1009, kind = "channel", name = "Talk", megagroup = true)).toChat(now)

        assertEquals(ChatKind.SUPERGROUP, chat.kind)
    }

    @Test
    fun `falls back to the username then the id for the title`() {
        assertEquals("ada", dialog(peer(id = 7, username = "ada")).toChat(now).title)
        assertEquals("7", dialog(peer(id = 7)).toChat(now).title)
    }

    @Test
    fun `a dialog with no last message has no preview and no timestamp`() {
        val chat = dialog(peer(id = 7, name = "Ada")).toChat(now)

        assertNull(chat.lastMessagePreview)
        assertNull(chat.lastMessageAt)
    }

    @Test
    fun `a text last message is the preview and its millisecond date is the timestamp`() {
        val instant = Instant.parse("2026-09-30T10:00:00Z")
        val chat = dialog(
            peer = peer(id = 7, name = "Ada"),
            lastMessage = message(id = 5, text = "hello", date = instant.toEpochMilli()),
        ).toChat(now)

        assertEquals("hello", chat.lastMessagePreview)
        assertEquals(instant, chat.lastMessageAt)
    }

    @Test
    fun `a media last message with no text previews its kind`() {
        val chat = dialog(
            peer = peer(id = 7, name = "Ada"),
            lastMessage = message(id = 5, media = Media(kind = "photo"), date = 1L),
        ).toChat(now)

        assertEquals("[photo]", chat.lastMessagePreview)
    }

    @Test
    fun `derives archived from the archive folder id`() {
        assertTrue(dialog(peer(id = 7), folderId = 1).toChat(now).archived)
        assertFalse(dialog(peer(id = 7), folderId = null).toChat(now).archived)
        assertFalse(dialog(peer(id = 7), folderId = 2).toChat(now).archived)
    }

    @Test
    fun `derives muted from a mute that runs into the future`() {
        assertTrue(dialog(peer(id = 7), muteUntil = now.toEpochMilli() + 1).toChat(now).muted)
        assertFalse(dialog(peer(id = 7), muteUntil = 0L).toChat(now).muted)
        assertFalse(dialog(peer(id = 7), muteUntil = now.toEpochMilli() - 1).toChat(now).muted)
        assertFalse(dialog(peer(id = 7)).toChat(now).muted)
    }

    @Test
    fun `maps a resolved peer without any dialog state`() {
        val chat = peer(id = 7, kind = "user", username = "ada", name = "Ada Lovelace").toChat()

        assertEquals(ChatKind.PRIVATE, chat.kind)
        assertNull(chat.lastMessagePreview)
        assertFalse(chat.archived)
        assertFalse(chat.muted)
    }
}
