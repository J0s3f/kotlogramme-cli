package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Media
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.protocol.MessageAction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageMapperTest {
    @Test
    fun `maps an outgoing edited reply with media`() {
        val instant = Instant.parse("2026-09-30T10:00:00Z")
        val sender = User(id = 1, username = "ada", firstName = "Ada", lastName = "Lovelace")

        val mapped = message(
            id = 42,
            text = "hi",
            outgoing = true,
            replyToMessageId = 41,
            date = instant.toEpochMilli(),
            editDate = instant.toEpochMilli() + 1_000,
            pinned = true,
            media = Media(
                kind = "video",
                duration = 12.5,
                width = 320,
                height = 240,
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                size = 4_000_000,
                name = "clip.mp4",
            ),
            sender = sender,
        ).toMessage()

        assertEquals(42, mapped.id)
        assertEquals("Ada Lovelace", mapped.senderName)
        assertEquals("hi", mapped.text)
        assertEquals(instant, mapped.sentAt)
        assertTrue(mapped.outgoing)
        assertTrue(mapped.edited)
        assertTrue(mapped.pinned)
        assertEquals(41, mapped.replyToMessageId)
        assertEquals(
            MediaInfo(
                kind = "video",
                durationSeconds = 12.5,
                width = 320,
                height = 240,
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                sizeBytes = 4_000_000,
                name = "clip.mp4",
            ),
            mapped.media,
        )
    }

    @Test
    fun `a plain message keeps the edited, pinned, reply and media flags absent`() {
        val mapped = message(id = 1, text = "plain", date = 0L).toMessage()

        assertFalse(mapped.edited)
        assertFalse(mapped.pinned)
        assertNull(mapped.replyToMessageId)
        assertNull(mapped.media)
        assertNull(mapped.action)
        assertEquals("", mapped.senderName)
    }

    @Test
    fun `maps a known service action to a phrase`() {
        val mapped = message(
            id = 7,
            action = MessageAction(messageId = 7, senderId = 1, kind = "pinMessage"),
        ).toMessage()

        assertEquals("pinned a message", mapped.action)
    }

    @Test
    fun `a kind this build does not name keeps the kind in the phrase`() {
        val mapped = message(
            id = 8,
            action = MessageAction(messageId = 8, kind = "someFutureAction"),
        ).toMessage()

        assertEquals("service action: someFutureAction", mapped.action)
        assertEquals("service action: unknown", serviceAction("unknown"))
    }

    @Test
    fun `falls back to the post author when the sender is absent`() {
        val mapped = message(id = 1, text = "x", postAuthor = "The Editor").toMessage()

        assertEquals("The Editor", mapped.senderName)
    }

    @Test
    fun `falls back to the peer name when there is no sender or author`() {
        val mapped = message(id = 1, text = "x", peer = peer(id = 9, name = "The Club")).toMessage()

        assertEquals("The Club", mapped.senderName)
    }

    @Test
    fun `falls back to the sender username when the names are blank`() {
        val sender = User(id = 1, username = "ada", firstName = "", lastName = "")

        val mapped = message(id = 1, text = "x", sender = sender).toMessage()

        assertEquals("ada", mapped.senderName)
    }

    @Test
    fun `carries the inline bot a message came via`() {
        val mapped = message(id = 1, text = "x", viaBotId = 99).toMessage()

        assertEquals(99L, mapped.viaBotId)
        assertNull(mapped.viaBotUsername)
    }
}
