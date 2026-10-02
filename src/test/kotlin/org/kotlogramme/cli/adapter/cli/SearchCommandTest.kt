package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                "id\ttime\tfrom\tvia\treply\tquote\tmedia\taction\ttext",
                "7\t2026-01-01T12:30:00Z\tAda Lovelace\t\t\t\t\t\thello",
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

    @Test
    fun `search passes the cursor to the use case`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        fixture.run("search", "hello", "--after", "99")

        assertEquals(listOf<String?>("99"), search.searchCursors)
    }

    @Test
    fun `search prints the next cursor on a full page`() {
        val messages = (1..20).map { index -> testMessage.copy(id = index) }
        val search = FakeSearchMessages(messages)
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("search", "hello")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 20"), fixture.output.text)
    }

    @Test
    fun `search omits the next cursor on a short page`() {
        val search = FakeSearchMessages(listOf(testMessage))
        val fixture = cliFixture(searchMessages = search)

        fixture.run("search", "hello")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `search rejects a malformed cursor`() {
        val search = FakeSearchMessages()
        val fixture = cliFixture(searchMessages = search)

        val result = fixture.run("search", "hello", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
    }
}
