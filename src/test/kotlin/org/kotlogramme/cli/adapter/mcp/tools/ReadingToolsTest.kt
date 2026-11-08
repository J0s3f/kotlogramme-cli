package org.kotlogramme.cli.adapter.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlogramme.cli.adapter.cli.FakeAuthenticate
import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeListDialogs
import org.kotlogramme.cli.adapter.cli.FakeListFolders
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeReadHistory
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.testAccount
import org.kotlogramme.cli.adapter.mcp.LEGACY_LATEST
import org.kotlogramme.cli.adapter.mcp.MODERN
import org.kotlogramme.cli.adapter.mcp.McpSchema
import org.kotlogramme.cli.adapter.mcp.callParams
import org.kotlogramme.cli.adapter.mcp.initializeRequest
import org.kotlogramme.cli.adapter.mcp.legacyRequest
import org.kotlogramme.cli.adapter.mcp.mcpServer
import org.kotlogramme.cli.adapter.mcp.modernRequest
import org.kotlogramme.cli.adapter.mcp.tool.AccessLevel
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult
import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.Folder
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Participant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingToolsTest {
    private val ada = Chat(1, "Ada", ChatKind.PRIVATE, "ada", "hi", Instant.parse("2026-01-01T12:00:00Z"))
    private val bob = Chat(2, "Bob", ChatKind.GROUP, null, null, null)

    private fun message(id: Int, text: String = "hello $id") =
        Message(id, "Ada Lovelace", text, Instant.parse("2026-01-01T12:30:00Z"), outgoing = false)

    private fun args(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Int -> put(key, value)
                is Boolean -> put(key, value)
            }
        }
    }

    private fun McpTool.run(vararg pairs: Pair<String, Any>): ToolResult = call(args(*pairs), ToolContext())

    private fun ToolResult.body(): JsonObject = structured!!.jsonObject

    private fun JsonObject.items(): List<JsonObject> = getValue("items").jsonArray.map { it.jsonObject }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

    // --- telegram_whoami

    @Test
    fun `whoami reports the signed in account`() {
        val tool = WhoamiTool { FakeAuthenticate(AccountStatus.SignedIn(testAccount)) }

        val body = tool.run().body()

        assertEquals("Signed in as Ada Lovelace", body.text("message"))
        assertEquals("42", body.text("id"))
        assertEquals("@ada", body.text("username"))
    }

    @Test
    fun `whoami without a session is an error that says how to log in`() {
        val result = WhoamiTool { FakeAuthenticate(AccountStatus.Anonymous) }.run()

        assertTrue(result.isError)
        assertTrue(result.text.contains("kotlogramme login"), result.text)
    }

    // --- telegram_list_chats

    @Test
    fun `list_chats returns the chats and asks for twenty by default`() {
        val dialogs = FakeListDialogs(listOf(ada, bob))

        val body = ListChatsTool { dialogs }.run().body()

        assertEquals(listOf(20), dialogs.limits)
        assertEquals(listOf(false), dialogs.alls)
        assertEquals(listOf("Ada", "Bob"), body.items().map { it.text("title") })
        assertNull(body["next_cursor"])
    }

    @Test
    fun `list_chats offers the next cursor when the page is full and takes it back`() {
        val dialogs = FakeListDialogs(listOf(ada))

        val body = ListChatsTool { dialogs }.run("limit" to 1).body()

        assertEquals(ListingCursor.formatDialogs(ada), body.text("next_cursor"))
        ListChatsTool { dialogs }.run("limit" to 1, "cursor" to body.text("next_cursor"))
        assertEquals(listOf(ListingCursor.formatDialogs(ada)), dialogs.cursors.filterNotNull())
    }

    @Test
    fun `list_chats rejects a limit outside one to a hundred`() {
        val tool = ListChatsTool { FakeListDialogs() }

        assertFailsWith<IllegalArgumentException> { tool.run("limit" to 0) }
        assertFailsWith<IllegalArgumentException> { tool.run("limit" to 101) }
    }

    // --- telegram_read_history

    @Test
    fun `read_history reads the chat and returns the messages`() {
        val history = FakeReadHistory(listOf(message(5), message(6)))

        val body = ReadHistoryTool({ history }).run("chat" to "@ada").body()

        assertEquals(listOf("hello 5", "hello 6"), body.items().map { it.text("text") })
        assertEquals(listOf("@ada" to 20), history.calls.map { it.reference to it.limit })
        assertNull(history.calls.single().beforeMessageId)
    }

    @Test
    fun `read_history continues from a cursor and offers the next one`() {
        val history = FakeReadHistory(listOf(message(40), message(41)))

        val body = ReadHistoryTool({ history }).run("chat" to "42", "limit" to 2, "cursor" to "100").body()

        assertEquals(100, history.calls.single().beforeMessageId)
        assertEquals("41", body.text("next_cursor"))
    }

    @Test
    fun `read_history rejects a cursor that is not a message id`() {
        val tool = ReadHistoryTool({ FakeReadHistory() })

        val problem = assertFailsWith<IllegalArgumentException> { tool.run("chat" to "@ada", "cursor" to "abc") }

        assertTrue(problem.message!!.contains("cursor"), problem.message)
    }

    @Test
    fun `read_history never reaches Telegram with an invite link`() {
        val history = FakeReadHistory(listOf(message(1)))

        assertFailsWith<IllegalArgumentException> {
            ReadHistoryTool({ history }).run("chat" to "https://t.me/+AbCdEf")
        }

        assertEquals(emptyList(), history.calls)
    }

    // --- search

    @Test
    fun `search_messages passes the query chat limit and cursor on`() {
        val search = FakeSearchMessages(results = listOf(message(9)))

        val body = SearchMessagesTool({ search }).run("query" to "invoice", "chat" to "@ada", "cursor" to "77").body()

        val call = search.searches.single()
        assertEquals(Triple("@ada", "invoice", 20), Triple(call.reference, call.query, call.limit))
        assertEquals(listOf<String?>("77"), search.searchCursors)
        assertEquals(listOf("hello 9"), body.items().map { it.text("text") })
    }

    @Test
    fun `search_messages without a chat searches everywhere`() {
        val search = FakeSearchMessages()

        SearchMessagesTool({ search }).run("query" to "invoice")

        assertNull(search.searches.single().reference)
    }

    @Test
    fun `count_messages returns the number of matches`() {
        val body = CountMessagesTool { FakeSearchMessages(totalResults = 9) }.run("query" to "invoice").body()

        assertEquals(JsonPrimitive(9), body.getValue("count"))
    }

    @Test
    fun `get_pinned_message returns the pinned message or says there is none`() {
        val found = GetPinnedMessageTool({ FakeMessageWriter(pinned = message(3)) }).run("chat" to "@ada").body()
        val none = GetPinnedMessageTool({ FakeMessageWriter(pinned = null) }).run("chat" to "@ada").body()

        assertEquals("hello 3", found.items().single().text("text"))
        assertEquals("No pinned message in @ada.", none.text("message"))
    }

    // --- people and structure

    @Test
    fun `list_contacts and search_contacts return contacts`() {
        val contact = Contact(7, "Ada Lovelace", "ada", "+15550100")
        val contacts = FakeContacts(contacts = listOf(contact), searchResults = listOf(contact))

        val listed = ListContactsTool { contacts }.run().body()
        val found = SearchContactsTool { contacts }.run("query" to "ada").body()

        assertEquals(listOf(50), contacts.limits)
        assertEquals(listOf("ada" to 50), contacts.searches)
        assertEquals(1, listed.items().size)
        assertEquals(1, found.items().size)
    }

    @Test
    fun `list_members pages by offset`() {
        val members = (1..2).map { Participant(it.toLong(), "Member $it", null, "member") }
        val chatMembers = FakeChatMembers(members)

        val body = ListMembersTool { chatMembers }.run("chat" to "@group", "limit" to 2, "cursor" to "10").body()

        assertEquals(listOf<String?>("10"), chatMembers.cursors)
        assertEquals("12", body.text("next_cursor"))
        assertEquals(2, body.items().size)
    }

    @Test
    fun `list_folders returns the folders`() {
        val body = ListFoldersTool { FakeListFolders(listOf(Folder(1, "Work", "custom"))) }.run().body()

        assertEquals("Work", body.items().single().text("title"))
    }

    // --- all of them

    @Test
    fun `every reading tool is read only and says so with all four hints`() {
        for (tool in everyTool()) {
            assertEquals(AccessLevel.READ_ONLY, tool.access, tool.name)
            assertTrue(tool.annotations.readOnly && !tool.annotations.destructive && tool.annotations.idempotent, tool.name)
        }
    }

    @Test
    fun `tool names are unique follow the naming rules and carry a real description`() {
        val tools = everyTool()

        assertEquals(tools.size, tools.map { it.name }.toSet().size)
        for (tool in tools) {
            assertTrue(Regex("telegram_[a-z_]+").matches(tool.name), tool.name)
            assertTrue(tool.description.length > 40, tool.name)
            assertTrue(tool.title.isNotBlank(), tool.name)
            assertEquals("object", tool.inputSchema.getValue("type").jsonPrimitive.content, tool.name)
            assertEquals(JsonPrimitive(false), tool.inputSchema.getValue("additionalProperties"), tool.name)
        }
    }

    @Test
    fun `an unknown argument is rejected by every tool`() {
        for (tool in everyTool()) {
            assertFailsWith<IllegalArgumentException>(tool.name) { tool.run("no_such_argument" to "x") }
        }
    }

    @Test
    fun `the tool list of the real catalog satisfies the protocol schema in both eras`() {
        val server = mcpServer(*everyTool().toTypedArray())

        McpSchema.assertResultResponse(MODERN, "ListToolsResult", server.handle(modernRequest(1, "tools/list")))
        server.handle(initializeRequest())
        McpSchema.assertResultResponse(LEGACY_LATEST, "ListToolsResult", server.handle(legacyRequest(2, "tools/list")))
    }

    @Test
    fun `every input schema is itself a valid JSON Schema`() {
        for (tool in everyTool()) {
            val problems = McpSchema.violationsOfSchema(tool.inputSchema.toString())

            assertEquals(emptyList(), problems, tool.name)
        }
    }

    @Test
    fun `a real call satisfies the call result schema`() {
        val server = mcpServer(ListFoldersTool { FakeListFolders(listOf(Folder(1, "Work", "custom"))) })

        val line = server.handle(modernRequest(3, "tools/call", callParams("telegram_list_folders")))

        McpSchema.assertResultResponse(MODERN, "CallToolResult", line)
    }

    private fun everyTool(): List<McpTool> = fakeTelegramTools().all().filter { it.access == AccessLevel.READ_ONLY }
}
