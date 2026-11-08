package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.FakeSessions
import org.kotlogramme.cli.adapter.cli.testMessage
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Session
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InspectionToolsTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `list_sessions shows every device with the hash that terminate_session takes`() {
        val sessions = FakeSessions(
            listOf(
                Session(111, "Pixel", "Android", "11.0", "203.0.113.5", "AT", now, current = true),
                Session(222, "", "Windows", "6.1", "198.51.100.7", "DE", now, current = false),
            ),
        )

        val items = fakeTelegramTools(sessions = sessions).tool("telegram_list_sessions").callWith().body().items()

        assertEquals(listOf("111", "222"), items.map { it.text("hash") })
        assertEquals(listOf("yes", ""), items.map { it.text("current") })
    }

    @Test
    fun `list_blocked pages by offset`() {
        val blocked = FakeContacts(blockedContacts = listOf(BlockedContact(5, "Spam Bot", "spam", now)))
        val tool = fakeTelegramTools(contacts = blocked).tool("telegram_list_blocked")

        val body = tool.callWith("limit" to 1, "cursor" to "10").body()

        assertEquals(listOf<String?>("10"), blocked.blockedCursors)
        assertEquals(listOf(1), blocked.blockedLimits)
        assertEquals(listOf(false), blocked.blockedAlls)
        assertEquals("11", body.text("next_cursor"))
    }

    @Test
    fun `list_blocked with a short page has no next cursor`() {
        val blocked = FakeContacts(blockedContacts = listOf(BlockedContact(5, "Spam Bot", "spam", now)))

        val body = fakeTelegramTools(contacts = blocked).tool("telegram_list_blocked").callWith().body()

        assertNull(body["next_cursor"])
    }

    @Test
    fun `list_files asks for every kind by default and offers the id of the last file as the cursor`() {
        val file = testMessage.copy(id = 31, media = MediaInfo(kind = "document", sizeBytes = 10, name = "a.pdf"))
        val search = FakeSearchMessages(fileResults = listOf(file))
        val tool = fakeTelegramTools(searchMessages = search).tool("telegram_list_files")

        val body = tool.callWith("chat" to "@ada", "limit" to 1).body()

        assertEquals(MediaFileKind.ALL, search.fileSearches.single().kind)
        assertEquals("31", body.text("next_cursor"))
        assertEquals("a.pdf", body.items().single().text("name"))
    }

    @Test
    fun `list_files filters by kind and refuses an unknown one`() {
        val search = FakeSearchMessages()
        val tool = fakeTelegramTools(searchMessages = search).tool("telegram_list_files")

        tool.callWith("chat" to "@ada", "kind" to "voice", "cursor" to "40")

        assertEquals(MediaFileKind.VOICE, search.fileSearches.single().kind)
        assertEquals(listOf<String?>("40"), search.fileCursors)
        assertFailsWith<IllegalArgumentException> { tool.callWith("chat" to "@ada", "kind" to "everything") }
    }
}
