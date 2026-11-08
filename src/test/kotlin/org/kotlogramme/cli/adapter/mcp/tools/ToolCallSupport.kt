package org.kotlogramme.cli.adapter.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlogramme.cli.FakeClock
import org.kotlogramme.cli.adapter.cli.FakeAdminRights
import org.kotlogramme.cli.adapter.cli.FakeAuthenticate
import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeDownloadMedia
import org.kotlogramme.cli.adapter.cli.FakeListDialogs
import org.kotlogramme.cli.adapter.cli.FakeListFolders
import org.kotlogramme.cli.adapter.cli.FakeListen
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeReadHistory
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.FakeSendMedia
import org.kotlogramme.cli.adapter.cli.FakeSessions
import org.kotlogramme.cli.adapter.mcp.tool.McpTool
import org.kotlogramme.cli.adapter.mcp.tool.ToolContext
import org.kotlogramme.cli.adapter.mcp.tool.ToolResult
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.spi.Clock

/** A JSON object of tool arguments; a `List` becomes a JSON array. */
internal fun toolArgs(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) -> put(key, jsonOf(value)) }
}

private fun jsonOf(value: Any): JsonElement =
    when (value) {
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { jsonOf(checkNotNull(it)) })
        else -> error("Unsupported test argument ${value::class}")
    }

internal fun McpTool.callWith(vararg pairs: Pair<String, Any>): ToolResult = call(toolArgs(*pairs), ToolContext())

internal fun ToolResult.body(): JsonObject = structured!!.jsonObject

internal fun JsonObject.items(): List<JsonObject> = getValue("items").jsonArray.map { it.jsonObject }

internal fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

/** The whole tool set on fakes; a test passes the one fake it wants to look at. */
internal fun fakeTelegramTools(
    messageWriter: MessageWriter = FakeMessageWriter(),
    chatMembers: ChatMembers = FakeChatMembers(),
    contacts: Contacts = FakeContacts(),
    adminRights: AdminRights = FakeAdminRights(),
    sessions: Sessions = FakeSessions(),
    searchMessages: SearchMessages = FakeSearchMessages(),
    sendMedia: SendMedia = FakeSendMedia(),
    downloadMedia: DownloadMedia = FakeDownloadMedia(),
    clock: Clock = FakeClock(),
): TelegramTools = TelegramTools(
    authenticate = { FakeAuthenticate() },
    listDialogs = { FakeListDialogs() },
    readHistory = { FakeReadHistory() },
    searchMessages = { searchMessages },
    contacts = { contacts },
    chatMembers = { chatMembers },
    listFolders = { FakeListFolders() },
    messageWriter = { messageWriter },
    adminRights = { adminRights },
    sessions = { sessions },
    listen = { FakeListen() },
    sendMedia = { sendMedia },
    downloadMedia = { downloadMedia },
    clock = clock,
)

internal fun TelegramTools.tool(name: String): McpTool = all().first { it.name == name }
