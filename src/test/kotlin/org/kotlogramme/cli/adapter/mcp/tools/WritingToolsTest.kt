package org.kotlogramme.cli.adapter.mcp.tools

import org.kotlogramme.cli.adapter.cli.ChatActionCall
import org.kotlogramme.cli.adapter.cli.EditCall
import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.ForwardCall
import org.kotlogramme.cli.adapter.cli.MessageIdCall
import org.kotlogramme.cli.adapter.cli.ReactCall
import org.kotlogramme.cli.adapter.cli.SendCall
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WritingToolsTest {
    private val writer = FakeMessageWriter()
    private val members = FakeChatMembers()
    private val contacts = FakeContacts()
    private val tools = fakeTelegramTools(messageWriter = writer, chatMembers = members, contacts = contacts)

    private fun call(name: String, vararg pairs: Pair<String, Any>) = tools.tool(name).callWith(*pairs)

    // --- sending

    @Test
    fun `send_message sends the text and returns the sent message`() {
        val body = call("telegram_send_message", "chat" to "@ada", "text" to "hello there").body()

        assertEquals(listOf(SendCall("@ada", "hello there", null, false)), writer.sends)
        assertEquals("hello", body.items().single().text("text"))
    }

    @Test
    fun `send_message passes a reply target and the silent flag on`() {
        call("telegram_send_message", "chat" to "42", "text" to "ok", "reply_to" to 9, "silent" to true)

        assertEquals(listOf(SendCall("42", "ok", 9, true)), writer.sends)
    }

    @Test
    fun `send_message keeps the text exactly as given`() {
        val text = "  line one\nline two ✓ 日本語  "

        call("telegram_send_message", "chat" to "me", "text" to text)

        assertEquals(text, writer.sends.single().text)
    }

    @Test
    fun `send_message refuses blank and over long text and an invite link before anything is sent`() {
        val send = tools.tool("telegram_send_message")

        assertFailsWith<IllegalArgumentException> { send.callWith("chat" to "@ada", "text" to "   ") }
        assertFailsWith<IllegalArgumentException> { send.callWith("chat" to "@ada", "text" to "x".repeat(4097)) }
        assertFailsWith<IllegalArgumentException> { send.callWith("chat" to "https://t.me/+AbCdEf", "text" to "hi") }
        assertFailsWith<IllegalArgumentException> { send.callWith("chat" to "@ada", "text" to "hi", "reply_to" to 0) }
        assertEquals(emptyList(), writer.sends)
    }

    @Test
    fun `a rejection of the use case reaches the model as an argument problem`() {
        val rejecting = fakeTelegramTools(messageWriter = FakeMessageWriter(rejection = IllegalArgumentException("nope")))

        val problem = assertFailsWith<IllegalArgumentException> {
            rejecting.tool("telegram_send_message").callWith("chat" to "@ada", "text" to "hi")
        }

        assertEquals("nope", problem.message)
    }

    // --- changing messages

    @Test
    fun `edit_message replaces the text`() {
        call("telegram_edit_message", "chat" to "@ada", "message_id" to 5, "text" to "fixed")

        assertEquals(listOf(EditCall("@ada", 5, "fixed")), writer.edits)
    }

    @Test
    fun `forward_messages forwards the ids to the target chat`() {
        call("telegram_forward_messages", "from_chat" to "@ada", "message_ids" to listOf(1, 2), "to_chat" to "@bob")

        assertEquals(listOf(ForwardCall("@ada", listOf(1, 2), "@bob")), writer.forwards)
    }

    @Test
    fun `forward_messages needs between one and a hundred distinct positive ids`() {
        val forward = tools.tool("telegram_forward_messages")
        fun forwardOf(ids: List<Int>) =
            forward.callWith("from_chat" to "@ada", "message_ids" to ids, "to_chat" to "@bob")

        assertFailsWith<IllegalArgumentException> { forwardOf(emptyList()) }
        assertFailsWith<IllegalArgumentException> { forwardOf((1..101).toList()) }
        assertFailsWith<IllegalArgumentException> { forwardOf(listOf(3, 3)) }
        assertFailsWith<IllegalArgumentException> { forwardOf(listOf(0)) }
        assertEquals(emptyList(), writer.forwards)
        forwardOf((1..100).toList())
        assertEquals(1, writer.forwards.size)
    }

    @Test
    fun `mark_read pin unpin react and remove_reaction name the message they act on`() {
        call("telegram_mark_read", "chat" to "@ada")
        call("telegram_pin_message", "chat" to "@ada", "message_id" to 4)
        call("telegram_unpin_message", "chat" to "@ada", "message_id" to 4)
        call("telegram_react", "chat" to "@ada", "message_id" to 4, "emoji" to " 👍 ")
        call("telegram_remove_reaction", "chat" to "@ada", "message_id" to 4)

        assertEquals(listOf("@ada"), writer.markedRead)
        assertEquals(listOf(MessageIdCall("@ada", 4)), writer.pins)
        assertEquals(listOf(MessageIdCall("@ada", 4)), writer.unpins)
        assertEquals(listOf(ReactCall("@ada", 4, "👍")), writer.reactions)
        assertEquals(listOf(MessageIdCall("@ada", 4)), writer.removals)
    }

    @Test
    fun `send_chat_action shows typing unless told otherwise and knows only the real statuses`() {
        call("telegram_send_chat_action", "chat" to "@ada")
        call("telegram_send_chat_action", "chat" to "@ada", "action" to "record-voice")

        assertEquals(
            listOf(ChatActionCall("@ada", ChatActivity.TYPING), ChatActionCall("@ada", ChatActivity.RECORD_VOICE)),
            writer.chatActions,
        )
        assertFailsWith<IllegalArgumentException> { call("telegram_send_chat_action", "chat" to "@ada", "action" to "dance") }
    }

    // --- people

    @Test
    fun `invite_member adds the user and refuses links and me`() {
        call("telegram_invite_member", "chat" to "@group", "user" to "@bob")

        assertEquals(listOf("@group" to "@bob"), members.invites)
        assertFailsWith<IllegalArgumentException> { call("telegram_invite_member", "chat" to "@group", "user" to "me") }
        assertFailsWith<IllegalArgumentException> { call("telegram_invite_member", "chat" to "@group", "user" to "t.me/bob") }
    }

    @Test
    fun `import_contact saves one contact and reports how many Telegram kept`() {
        val kept = FakeContacts(importedContacts = listOf(Contact(7, "Ada Lovelace", null, "+15550100")))
        val tool = fakeTelegramTools(contacts = kept).tool("telegram_import_contact")

        val body = tool.callWith("phone" to " +15550100 ", "first_name" to "Ada", "last_name" to "Lovelace").body()

        assertEquals(listOf(listOf(ContactToImport("+15550100", "Ada", "Lovelace"))), kept.imports)
        assertEquals("1", body.text("count"))
    }

    @Test
    fun `import_contact without a last name saves an empty one`() {
        call("telegram_import_contact", "phone" to "+15550100", "first_name" to "Ada")

        assertEquals(listOf(listOf(ContactToImport("+15550100", "Ada", ""))), contacts.imports)
    }

    @Test
    fun `import_contact reports a retry request`() {
        val retry = FakeContacts(retryCount = 2)

        val body = fakeTelegramTools(contacts = retry).tool("telegram_import_contact")
            .callWith("phone" to "+15550100", "first_name" to "Ada").body()

        assertTrue(body.text("message").contains("retry 2"), body.toString())
        assertEquals(ContactImportSummary(emptyList(), 2).retryCount, 2)
    }

    // --- the contract of the whole tier

    @Test
    fun `every writing tool needs read-write and promises neither read only nor destruction`() {
        val writing = tools.all().filter { it.access == AccessLevel.READ_WRITE }

        assertEquals(11, writing.size)
        for (tool in writing) {
            assertTrue(!tool.annotations.readOnly && !tool.annotations.destructive, tool.name)
        }
    }
}
