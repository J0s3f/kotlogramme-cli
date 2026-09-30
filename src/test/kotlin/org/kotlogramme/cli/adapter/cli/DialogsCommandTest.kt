package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
