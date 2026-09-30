package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCommandTest {
    @Test
    fun `search without --in is global`() {
        val search = FakeSearchMessages(listOf(testMessage))
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("search", "hello")

        assertEquals(0, result.statusCode)
        assertEquals(SearchCall(null, "hello", 20), search.searches.single())
        assertEquals(
            listOf(
                "id\ttime\tfrom\tvia\treply\tmedia\taction\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t\t\t\t\thello",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `search with --in passes the chat and the limit`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        fixture.run("search", "hello", "--in", "@ada", "--limit", "5")

        assertEquals(SearchCall("@ada", "hello", 5), search.searches.single())
    }

    @Test
    fun `search with --total prints the count`() {
        val search = FakeSearchMessages(totalResults = 42)
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("search", "hello", "--total")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("42"), fixture.output.lines)
        assertEquals(SearchCall(null, "hello", 0), search.totals.single())
        assertEquals(emptyList(), search.searches)
    }
}
