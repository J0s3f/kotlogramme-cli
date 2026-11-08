package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.FakeClock
import org.kotlogramme.cli.adapter.cli.AdminRefs
import org.kotlogramme.cli.adapter.cli.DeleteCall
import org.kotlogramme.cli.adapter.cli.FakeAdminRights
import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeSessions
import org.kotlogramme.cli.adapter.cli.PromoteCall
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestructiveToolsTest {
    private val writer = FakeMessageWriter(deletedCount = 2)
    private val members = FakeChatMembers()
    private val contacts = FakeContacts()
    private val rights = FakeAdminRights()
    private val sessions = FakeSessions()
    private val clock = FakeClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val tools = fakeTelegramTools(
        messageWriter = writer,
        chatMembers = members,
        contacts = contacts,
        adminRights = rights,
        sessions = sessions,
        clock = clock,
    )

    private fun call(name: String, vararg pairs: Pair<String, Any>) = tools.tool(name).callWith(*pairs)

    // --- messages

    @Test
    fun `delete_messages deletes the ids and reports how many went`() {
        val body = call("telegram_delete_messages", "chat" to "@ada", "message_ids" to listOf(1, 2)).body()

        assertEquals(listOf(DeleteCall("@ada", listOf(1, 2))), writer.deletes)
        assertEquals("2", body.text("count"))
    }

    @Test
    fun `delete_messages refuses an empty or oversized list before deleting anything`() {
        assertFailsWith<IllegalArgumentException> {
            call("telegram_delete_messages", "chat" to "@ada", "message_ids" to emptyList<Int>())
        }
        assertFailsWith<IllegalArgumentException> {
            call("telegram_delete_messages", "chat" to "@ada", "message_ids" to (1..101).toList())
        }
        assertEquals(emptyList(), writer.deletes)
    }

    @Test
    fun `unpin_all_messages clears the pins of the chat`() {
        call("telegram_unpin_all_messages", "chat" to "@ada")

        assertEquals(listOf("@ada"), writer.unpinnedAll)
    }

    // --- members

    @Test
    fun `kick_member removes the user and cannot be turned on the account itself`() {
        call("telegram_kick_member", "chat" to "@group", "user" to "123")

        assertEquals(listOf("@group" to "123"), members.kicks)
        assertFailsWith<IllegalArgumentException> { call("telegram_kick_member", "chat" to "@group", "user" to "me") }
        assertFailsWith<IllegalArgumentException> { call("telegram_kick_member", "chat" to "@group", "user" to "@me") }
        assertEquals(1, members.kicks.size)
    }

    @Test
    fun `restrict_member bans for a day and takes every ability away by default`() {
        call("telegram_restrict_member", "chat" to "@group", "user" to "@bob")

        val applied = rights.restrictions.single()
        assertEquals(Instant.parse("2026-03-02T10:00:00Z"), applied.restrictions.untilDate)
        assertEquals(ChatRestrictions.NONE_ALLOWED.copy(untilDate = applied.restrictions.untilDate), applied.restrictions)
    }

    @Test
    fun `restrict_member keeps the named abilities and lasts as long as asked`() {
        call(
            "telegram_restrict_member",
            "chat" to "@group",
            "user" to "@bob",
            "allow" to listOf("view-messages", "send-messages"),
            "hours" to 3,
        )

        val applied = rights.restrictions.single().restrictions
        assertTrue(applied.viewMessages && applied.sendMessages && !applied.sendMedia)
        assertEquals(Instant.parse("2026-03-01T13:00:00Z"), applied.untilDate)
    }

    @Test
    fun `restrict_member forever has no end date`() {
        call("telegram_restrict_member", "chat" to "@group", "user" to "@bob", "forever" to true)

        assertNull(rights.restrictions.single().restrictions.untilDate)
    }

    @Test
    fun `restrict_member rejects an unknown ability a bad duration and a repeat`() {
        fun restrict(vararg more: Pair<String, Any>) =
            call("telegram_restrict_member", "chat" to "@group", "user" to "@bob", *more)

        assertFailsWith<IllegalArgumentException> { restrict("allow" to listOf("fly")) }
        assertFailsWith<IllegalArgumentException> { restrict("allow" to listOf("send-media", "send-media")) }
        assertFailsWith<IllegalArgumentException> { restrict("hours" to 0) }
        assertFailsWith<IllegalArgumentException> { restrict("hours" to 100_000) }
        assertEquals(emptyList(), rights.restrictions)
    }

    @Test
    fun `set_admin_rights grants exactly the named rights`() {
        call(
            "telegram_set_admin_rights",
            "chat" to "@group",
            "user" to "@bob",
            "grant" to listOf("pin-messages", "delete-messages"),
        )

        val expected = ChatRights(pinMessages = true, deleteMessages = true)
        assertEquals(listOf(PromoteCall("@group", "@bob", expected)), rights.promotions)
    }

    @Test
    fun `set_admin_rights with an empty grant removes the admin rights`() {
        call("telegram_set_admin_rights", "chat" to "@group", "user" to "@bob", "grant" to emptyList<String>())

        assertEquals(ChatRights.NONE, rights.promotions.single().rights)
    }

    @Test
    fun `set_admin_rights must be told what to grant`() {
        assertFailsWith<IllegalArgumentException> { call("telegram_set_admin_rights", "chat" to "@group", "user" to "@bob") }
        assertFailsWith<IllegalArgumentException> {
            call("telegram_set_admin_rights", "chat" to "@group", "user" to "@bob", "grant" to listOf("everything"))
        }
        assertEquals(emptyList(), rights.promotions)
    }

    // --- people and sessions

    @Test
    fun `block unblock and delete_contact act on the user`() {
        call("telegram_block_user", "user" to "@bob")
        call("telegram_unblock_user", "user" to "@bob")
        call("telegram_delete_contact", "user" to "77")

        assertEquals(listOf("@bob"), contacts.blocked)
        assertEquals(listOf("@bob"), contacts.unblocked)
        assertEquals(listOf("77"), contacts.deleted)
    }

    @Test
    fun `block refuses the account itself and links`() {
        assertFailsWith<IllegalArgumentException> { call("telegram_block_user", "user" to "me") }
        assertFailsWith<IllegalArgumentException> { call("telegram_block_user", "user" to "https://t.me/bob") }
        assertEquals(emptyList(), contacts.blocked)
    }

    @Test
    fun `terminate_session takes the exact hash of the listing`() {
        call("telegram_terminate_session", "session_hash" to "9223372036854775807")

        assertEquals(listOf(Long.MAX_VALUE), sessions.terminated)
        assertFailsWith<IllegalArgumentException> { call("telegram_terminate_session", "session_hash" to "latest") }
        assertEquals(1, sessions.terminated.size)
    }

    @Test
    fun `terminate_all_sessions needs the confirmation to be true`() {
        assertFailsWith<IllegalArgumentException> { call("telegram_terminate_all_sessions") }
        assertFailsWith<IllegalArgumentException> { call("telegram_terminate_all_sessions", "confirm" to false) }
        assertEquals(0, sessions.terminateAllCount)

        call("telegram_terminate_all_sessions", "confirm" to true)

        assertEquals(1, sessions.terminateAllCount)
    }

    // --- the contract of the whole tier

    @Test
    fun `every destructive tool needs full access and says it is destructive`() {
        val destructive = tools.all().filter { it.access == AccessLevel.FULL }

        assertEquals(10, destructive.size)
        for (tool in destructive) {
            assertTrue(!tool.annotations.readOnly && tool.annotations.destructive, tool.name)
        }
    }

    @Test
    fun `admin rights details of a member are readable`() {
        val granted = FakeAdminRights(rights = ChatRights(pinMessages = true))
        val tool = fakeTelegramTools(adminRights = granted).tool("telegram_get_member_rights")

        val rows = tool.callWith("chat" to "@group", "user" to "@bob").body().items()

        assertEquals(listOf(AdminRefs("@group", "@bob")), granted.permissionCalls)
        assertEquals("granted", rows.first { it.text("right") == "pin-messages" }.text("status"))
        assertEquals("denied", rows.first { it.text("right") == "add-admins" }.text("status"))
    }
}
