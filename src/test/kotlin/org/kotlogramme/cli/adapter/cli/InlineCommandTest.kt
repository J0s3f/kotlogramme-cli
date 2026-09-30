package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.InlineQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineCommandTest {
    private val answer = InlineQuery(
        queryId = 42L,
        results = listOf(
            inlineResult("a", "First", text = "one"),
            inlineResult("b", "Second", text = "two"),
        ),
    )

    @Test
    fun `inline lists the numbered results of the answer`() {
        val bots = FakeInlineBots(answer)
        val fixture = cliFixture(inline = bots)

        val result = fixture.run("inline", "@gif", "cats", "--in", "@club")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(InlineQueryCall("@gif", "cats", "@club")), bots.queries)
        assertEquals(
            listOf(
                "index\tid\ttype\ttitle\tdescription\ttext",
                "0\ta\tarticle\tFirst\t\tone",
                "1\tb\tarticle\tSecond\t\ttwo",
            ),
            fixture.output.lines,
        )
        assertTrue(bots.sends.isEmpty())
    }

    @Test
    fun `inline send posts the chosen result with the query id carried from the answer`() {
        val bots = FakeInlineBots(answer)
        val fixture = cliFixture(inline = bots)

        val result = fixture.run("inline", "@gif", "cats", "--send", "1", "--to", "@club")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(InlineSendCall("@club", 42L, "b")), bots.sends)
        assertEquals("7\t2026-01-01T12:30:00Z\tAda Lovelace\t\t\t\thello", fixture.output.lines.last())
    }

    @Test
    fun `inline send without a destination is a usage error`() {
        val bots = FakeInlineBots(answer)
        val fixture = cliFixture(inline = bots)

        val result = fixture.run("inline", "@gif", "cats", "--send", "0")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--send needs --to"), "stderr was: ${result.stderr}")
        assertTrue(bots.sends.isEmpty())
    }

    @Test
    fun `inline send past the end of the answer is a usage error`() {
        val bots = FakeInlineBots(answer)
        val fixture = cliFixture(inline = bots)

        val result = fixture.run("inline", "@gif", "cats", "--send", "9", "--to", "@club")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("No result at index 9"), "stderr was: ${result.stderr}")
        assertTrue(bots.sends.isEmpty())
    }

    @Test
    fun `inline reports a rejected query as a usage error`() {
        val bots = FakeInlineBots(rejection = IllegalArgumentException("query must not be blank"))
        val fixture = cliFixture(inline = bots)

        val result = fixture.run("inline", "@gif", "cats")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("must not be blank"), "stderr was: ${result.stderr}")
    }
}
