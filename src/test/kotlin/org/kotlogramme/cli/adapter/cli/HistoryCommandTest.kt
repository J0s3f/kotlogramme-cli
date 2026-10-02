package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryCommandTest {
    @Test
    fun `history reads the peer with the limit and the cursor`() {
        val history = FakeReadHistory(listOf(testMessage))
        val fixture = cliFixture(readHistory = history)

        val result = fixture.run("history", "@ada", "--limit", "10", "--before", "99")

        assertEquals(0, result.statusCode)
        assertEquals(HistoryCall("@ada", 10, 99), history.calls.single())
        assertEquals(
            listOf(
                "id\ttime\tfrom\tvia\treply\tquote\tmedia\taction\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t\t\t\t\t\thello",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `history defaults to a page with no cursor`() {
        val history = FakeReadHistory()
        val fixture = cliFixture(readHistory = history)

        fixture.run("history", "42")

        assertEquals(HistoryCall("42", 20, null), history.calls.single())
    }

    @Test
    fun `history --after is an alias for --before`() {
        val history = FakeReadHistory(listOf(testMessage))
        val fixture = cliFixture(readHistory = history)

        fixture.run("history", "@ada", "--after", "55")

        assertEquals(HistoryCall("@ada", 20, 55), history.calls.single())
    }

    @Test
    fun `history --before still works`() {
        val history = FakeReadHistory(listOf(testMessage))
        val fixture = cliFixture(readHistory = history)

        fixture.run("history", "@ada", "--before", "77")

        assertEquals(HistoryCall("@ada", 20, 77), history.calls.single())
    }

    @Test
    fun `history prints the next cursor on a full page`() {
        val messages = (1..20).map { index -> testMessage.copy(id = index) }
        val history = FakeReadHistory(messages)
        val fixture = cliFixture(readHistory = history)

        val result = fixture.run("history", "@ada")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 20"), fixture.output.text)
    }

    @Test
    fun `history omits the next cursor on a short page`() {
        val history = FakeReadHistory(listOf(testMessage))
        val fixture = cliFixture(readHistory = history)

        fixture.run("history", "@ada")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `history rejects a malformed --after`() {
        val history = FakeReadHistory(listOf(testMessage))
        val fixture = cliFixture(readHistory = history)

        val result = fixture.run("history", "@ada", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("invalid value for --after"), "stderr was: ${result.stderr}")
    }
}
