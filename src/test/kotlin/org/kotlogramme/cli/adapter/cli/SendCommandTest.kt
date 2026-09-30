package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendCommandTest {
    @Test
    fun `send joins the words and passes the reply and silent flags`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("send", "@ada", "hello", "there", "--reply-to", "5", "--silent")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendCall("@ada", "hello there", 5, true)), writer.sends)
    }

    @Test
    fun `send reads the message from stdin when the text is a dash`() {
        val writer = FakeMessageWriter()
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("send", "@ada", "-", stdin = "hello from stdin")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendCall("@ada", "hello from stdin", null, false)), writer.sends)
    }

    @Test
    fun `send reports a rejected input as a usage error`() {
        val writer = FakeMessageWriter(
            rejection = IllegalArgumentException("message id must be positive but was 0"),
        )
        val fixture = cliFixture(messageWriter = writer)

        val result = fixture.run("send", "@ada", "hello", "--reply-to", "0")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("must be positive"), "stderr was: ${result.stderr}")
        assertTrue(writer.sends.isEmpty())
    }
}
