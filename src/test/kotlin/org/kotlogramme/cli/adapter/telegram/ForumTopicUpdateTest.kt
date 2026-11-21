package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TypedUpdate
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.protocol.ForumTopicEditUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ForumTopicUpdateTest {
    private fun update(edit: ForumTopicEditUpdate) = TypedUpdate(kind = "forumTopicEdited", forumTopicEdit = edit)

    private fun topicEdited(edit: ForumTopicEditUpdate) =
        assertIs<IncomingUpdate.TopicEdited>(update(edit).toIncomingUpdate())

    @Test
    fun `an edited forum topic names its chat and topic`() {
        val edited = topicEdited(ForumTopicEditUpdate(peerId = 5, peerKind = "channel", topicId = 7))

        assertEquals(5, edited.chatId)
        assertEquals(7, edited.topicId)
    }

    @Test
    fun `only the fields Telegram reports as changed are carried`() {
        val edited = topicEdited(
            ForumTopicEditUpdate(peerId = 5, peerKind = "channel", topicId = 7, title = "News", closed = true),
        )

        assertEquals("News", edited.title)
        assertEquals(true, edited.closed)
        assertEquals(null, edited.hidden)
        assertEquals(null, edited.iconEmojiId)
    }

    @Test
    fun `a change of icon and visibility is carried`() {
        val edited = topicEdited(
            ForumTopicEditUpdate(peerId = 5, peerKind = "channel", topicId = 7, iconEmojiId = 99, hidden = false),
        )

        assertEquals(99, edited.iconEmojiId)
        assertEquals(false, edited.hidden)
    }

    @Test
    fun `an update of another kind is not mistaken for a topic edit`() {
        val other = TypedUpdate(kind = "typing")

        assertIs<IncomingUpdate.Other>(other.toIncomingUpdate())
    }
}
