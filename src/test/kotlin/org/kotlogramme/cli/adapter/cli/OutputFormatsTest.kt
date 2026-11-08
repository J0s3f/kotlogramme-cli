package org.kotlogramme.cli.adapter.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every command that prints says it in every format.
 *
 * The sweep runs each command against fakes in the JSON and the plain format and checks what a
 * program relies on: JSON output is one valid JSON value per line, and plain output carries no table
 * borders. The commands that print a statement rather than a table are also checked for their exact
 * JSON shape.
 */
class OutputFormatsTest {
    private val invocations: List<List<String>> = listOf(
        listOf("dialogs"),
        listOf("history", "@ada"),
        listOf("send", "@ada", "hi"),
        listOf("whoami"),
        listOf("config"),
        listOf("contacts"),
        listOf("search-contacts", "ada"),
        listOf("block", "@ada"),
        listOf("unblock", "@ada"),
        listOf("blocked"),
        listOf("import-contacts", "+15550100", "Ada"),
        listOf("delete-contact", "@ada"),
        listOf("delete", "@ada", "1"),
        listOf("pin", "@ada", "1"),
        listOf("pinned", "@ada"),
        listOf("react", "@ada", "1", "👍"),
        listOf("unreact", "@ada", "1"),
        listOf("mark-read", "@ada"),
        listOf("chat-action", "@ada", "--action", "typing"),
        listOf("search", "hello", "--in", "@ada"),
        listOf("search", "hello", "--total"),
        listOf("members", "@ada"),
        listOf("invite", "@ada", "@bob"),
        listOf("kick", "@ada", "@bob"),
        listOf("sessions"),
        listOf("sessions", "terminate-all", "--yes"),
        listOf("folders"),
        listOf("stickers"),
        listOf("list-files", "@ada"),
        listOf("doctor"),
        listOf("logout"),
        listOf("edit", "@ada", "7", "fixed"),
        listOf("permissions", "@ada", "@bob"),
        listOf("promote", "@ada", "@bob"),
        listOf("restrict", "@ada", "@bob"),
        listOf("chat-photo-history", "@ada"),
        listOf("profile-photos", "@ada"),
        listOf("unpin", "@ada", "1"),
        listOf("inline", "@bot", "cats"),
    )

    private fun lines(format: OutputFormat, vararg args: String): List<String> {
        val output = RecordingOutput(format)
        val result = cliFixture(output = output).run(*args)
        assertEquals(0, result.statusCode, "${args.toList()} failed: ${result.stderr}${result.stdout}")
        return output.lines
    }

    @Test
    fun `every command prints only JSON values in the json format`() {
        for (arguments in invocations) {
            for (line in lines(OutputFormat.JSON, *arguments.toTypedArray())) {
                runCatching { Json.parseToJsonElement(line) }
                    .onFailure { throw AssertionError("${arguments}: not JSON: $line", it) }
            }
        }
    }

    @Test
    fun `every command prints no table borders in the plain format`() {
        for (arguments in invocations) {
            for (line in lines(OutputFormat.PLAIN, *arguments.toTypedArray())) {
                assertTrue(!line.startsWith("+-") && !line.startsWith("| "), "$arguments: bordered: $line")
            }
        }
    }

    @Test
    fun `a statement is a message object in the json format`() {
        assertEquals(listOf("""{"message":"Blocked @ada."}"""), lines(OutputFormat.JSON, "block", "@ada"))
    }

    @Test
    fun `a statement is a bare line in the plain format`() {
        assertEquals(listOf("Blocked @ada."), lines(OutputFormat.PLAIN, "block", "@ada"))
    }

    @Test
    fun `a message count is an object in the json format`() {
        val output = RecordingOutput(OutputFormat.JSON)
        val search = FakeSearchMessages(totalResults = 9)

        cliFixture(output = output, searchMessages = search).run("search", "hello", "--total")

        assertEquals(listOf("""{"count":9}"""), output.lines)
    }

    @Test
    fun `a full history page ends with the next cursor in the json format`() {
        val message = Message(7, "Ada", "hello", Instant.parse("2026-01-01T00:00:00Z"), outgoing = false)
        val output = RecordingOutput(OutputFormat.JSON)

        cliFixture(output = output, readHistory = FakeReadHistory(listOf(message)))
            .run("history", "@ada", "--limit", "1")

        val last = Json.parseToJsonElement(output.lines.last()).jsonObject
        assertEquals("7", last.getValue("next_after").jsonPrimitive.content)
    }

    @Test
    fun `a full history page ends with the next cursor comment in the plain format`() {
        val message = Message(7, "Ada", "hello", Instant.parse("2026-01-01T00:00:00Z"), outgoing = false)
        val output = RecordingOutput(OutputFormat.PLAIN)

        cliFixture(output = output, readHistory = FakeReadHistory(listOf(message)))
            .run("history", "@ada", "--limit", "1")

        assertEquals("# next: --after 7", output.lines.last())
    }

    @Test
    fun `config prints its settings as one object in the json format`() {
        val output = RecordingOutput(OutputFormat.JSON)

        cliFixture(output = output).run("config")

        val settings: JsonObject = Json.parseToJsonElement(output.lines.single()).jsonObject
        assertEquals(setOf("config_directory", "session_path", "credentials", "output_format"), settings.keys)
    }

    @Test
    fun `config prints tab separated pairs in the plain format`() {
        val output = RecordingOutput(OutputFormat.PLAIN)

        cliFixture(output = output).run("config")

        assertTrue(output.lines.all { it.substringBefore('\t') in setOf("config_directory", "session_path", "credentials", "output_format") })
        assertEquals(4, output.lines.size)
    }

    @Test
    fun `whoami prints the signed in account as one object in the json format`() {
        val output = RecordingOutput(OutputFormat.JSON)

        cliFixture(output = output, authenticate = FakeAuthenticate(AccountStatus.SignedIn(testAccount))).run("whoami")

        val account: JsonObject = Json.parseToJsonElement(output.lines.single()).jsonObject
        assertEquals("42", account.getValue("id").jsonPrimitive.content)
        assertEquals("@ada", account.getValue("username").jsonPrimitive.content)
        assertEquals("Signed in as Ada Lovelace", account.getValue("message").jsonPrimitive.content)
    }
}
