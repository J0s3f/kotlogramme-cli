package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogsCommandTest {
    private val chats = listOf(
        Chat(
            id = 1,
            title = "Ada",
            kind = ChatKind.PRIVATE,
            username = "ada",
            lastMessagePreview = "hi",
            lastMessageAt = null,
            unreadCount = 3,
            pinned = true,
        ),
        Chat(
            id = 2,
            title = "Team",
            kind = ChatKind.GROUP,
            username = null,
            lastMessagePreview = "bye",
            lastMessageAt = null,
        ),
    )

    @Test
    fun `dialogs renders the chat list with unread and pinned markers`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        val result = fixture.run("dialogs")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "id\tkind\ttitle\tunread\tpinned\tlast",
                "1\tprivate\tAda\t3\tyes\thi",
                "2\tgroup\tTeam\t0\t\tbye",
            ),
            fixture.output.lines,
        )
        assertEquals(listOf(20), listDialogs.limits)
    }

    @Test
    fun `dialogs asks for the requested limit`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs", "--limit", "5")

        assertEquals(listOf(5), listDialogs.limits)
    }

    @Test
    fun `dialogs passes the cursor and all to the use case`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs", "--after", "7:5:1000", "--all")

        assertEquals(listOf<String?>("7:5:1000"), listDialogs.cursors)
        assertEquals(listOf(true), listDialogs.alls)
    }

    @Test
    fun `dialogs defaults to no cursor and not all`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs")

        assertEquals(listOf<String?>(null), listDialogs.cursors)
        assertEquals(listOf(false), listDialogs.alls)
    }

    @Test
    fun `dialogs prints the next cursor on a full page`() {
        val full = (1..20).map { index ->
            Chat(
                id = index.toLong(),
                title = "Chat $index",
                kind = ChatKind.PRIVATE,
                username = null,
                lastMessagePreview = null,
                lastMessageAt = Instant.ofEpochMilli(index * 1000L),
                topMessageId = index * 10,
            )
        }
        val listDialogs = FakeListDialogs(full)
        val fixture = cliFixture(listDialogs = listDialogs)

        val result = fixture.run("dialogs", "--limit", "20")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 20:200:20000"), fixture.output.text)
    }

    @Test
    fun `dialogs omits the next cursor on a short page`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `dialogs excludes the Saved Messages anchor from the page count and cursor`() {
        val savedMessages = Chat(
            id = 1,
            title = "Saved Messages",
            kind = ChatKind.PRIVATE,
            username = null,
            lastMessagePreview = null,
            lastMessageAt = Instant.ofEpochMilli(5000L),
            topMessageId = 5,
            isSelf = true,
        )
        val real = (2..21).map { index ->
            Chat(
                id = index.toLong(),
                title = "Chat $index",
                kind = ChatKind.PRIVATE,
                username = null,
                lastMessagePreview = null,
                lastMessageAt = Instant.ofEpochMilli(index * 1000L),
                topMessageId = index * 10,
            )
        }
        val listDialogs = FakeListDialogs(listOf(savedMessages) + real)
        val fixture = cliFixture(listDialogs = listDialogs)

        val result = fixture.run("dialogs", "--limit", "20")

        assertEquals(0, result.statusCode)
        // 20 real dialogs fill the page; the anchor does not count toward the limit, and the
        // cursor is the last real dialog, not the anchor.
        assertTrue(fixture.output.text.contains("# next: --after 21:210:21000"), fixture.output.text)
    }

    @Test
    fun `dialogs does not print the next cursor when only the anchor and a short page arrive`() {
        val savedMessages = Chat(
            id = 1,
            title = "Saved Messages",
            kind = ChatKind.PRIVATE,
            username = null,
            lastMessagePreview = null,
            lastMessageAt = Instant.ofEpochMilli(5000L),
            topMessageId = 5,
            isSelf = true,
        )
        val listDialogs = FakeListDialogs(listOf(savedMessages))
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `dialogs omits the next cursor when all is set`() {
        val full = (1..20).map { index ->
            Chat(
                id = index.toLong(),
                title = "Chat $index",
                kind = ChatKind.PRIVATE,
                username = null,
                lastMessagePreview = null,
                lastMessageAt = Instant.ofEpochMilli(index * 1000L),
                topMessageId = index * 10,
            )
        }
        val listDialogs = FakeListDialogs(full)
        val fixture = cliFixture(listDialogs = listDialogs)

        fixture.run("dialogs", "--limit", "20", "--all")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `dialogs rejects a malformed cursor`() {
        val listDialogs = FakeListDialogs(chats)
        val fixture = cliFixture(listDialogs = listDialogs)

        val result = fixture.run("dialogs", "--after", "not-a-cursor")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
    }
}
