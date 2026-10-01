package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
