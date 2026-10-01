package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.ChatActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageWriteCommandTest {
    @Test
    fun `edit sends the joined text and the id`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("edit", "@ada", "7", "new", "text")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(EditCall("@ada", 7, "new text")), writer.edits)
    }

    @Test
    fun `delete sends every id and reports the count`() {
        val writer = FakeMessageWriter(deletedCount = 3)
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("delete", "@ada", "1", "2", "3")

        assertEquals(listOf(DeleteCall("@ada", listOf(1, 2, 3))), writer.deletes)
        assertEquals(listOf("Deleted 3 message(s)."), fixture.output.lines)
    }

    @Test
    fun `forward carries the source, the ids and the destination`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("forward", "@ada", "1", "2", "--to", "@group")

        assertEquals(listOf(ForwardCall("@ada", listOf(1, 2), "@group")), writer.forwards)
    }

    @Test
    fun `pin and unpin name the message`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("pin", "@ada", "7")
        fixture.run("unpin", "@ada", "8")

        assertEquals(listOf(MessageIdCall("@ada", 7)), writer.pins)
        assertEquals(listOf(MessageIdCall("@ada", 8)), writer.unpins)
    }

    @Test
    fun `react and unreact name the message`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("react", "@ada", "7", "thumbsup")
        fixture.run("unreact", "@ada", "8")

        assertEquals(listOf(ReactCall("@ada", 7, "thumbsup")), writer.reactions)
        assertEquals(listOf(MessageIdCall("@ada", 8)), writer.removals)
    }

    @Test
    fun `mark-read marks the chat`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("mark-read", "@ada")

        assertEquals(listOf("@ada"), writer.markedRead)
    }

    @Test
    fun `chat-action defaults to typing`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("chat-action", "@ada")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(ChatActionCall("@ada", ChatActivity.TYPING)), writer.chatActions)
        assertEquals(listOf("Sent the 'typing' action to @ada."), fixture.output.lines)
    }

    @Test
    fun `chat-action maps a named status`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        fixture.run("chat-action", "@ada", "--action", "upload-photo")

        assertEquals(listOf(ChatActionCall("@ada", ChatActivity.UPLOAD_PHOTO)), writer.chatActions)
    }

    @Test
    fun `chat-action rejects an unknown status`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("chat-action", "@ada", "--action", "moonwalk")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("unknown chat action"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), writer.chatActions)
    }

    @Test
    fun `delete reports a rejected input as a usage error`() {
        val writer = FakeMessageWriter(
            rejection = IllegalArgumentException("message id must be positive but was 0"),
        )
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("delete", "@ada", "0")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("must be positive"), "stderr was: ${result.stderr}")
        assertTrue(writer.deletes.isEmpty())
    }
}
