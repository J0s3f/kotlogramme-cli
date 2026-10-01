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
        val fixture = cliFixture(
            messageWriter = writer,
            sendCommand = sendWithUtf8("hello from stdin"),
        )

        val result = fixture.run("send", "@ada", "-")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendCall("@ada", "hello from stdin", null, false)), writer.sends)
    }

    @Test
    fun `send decodes piped stdin as UTF-8, not the platform charset`() {
        val writer = FakeMessageWriter()
        val body = "Cyrillic \u041f\u0440\u0438\u0432\u0435\u0442, CJK \u4f60\u597d, emoji \uD83D\uDE00"
        val fixture = cliFixture(messageWriter = writer, sendCommand = sendWithUtf8(body))

        val result = fixture.run("send", "@ada", "-")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(SendCall("@ada", body, null, false)), writer.sends)
    }

    @Test
    fun `send preserves a supplementary character from piped stdin`() {
        val writer = FakeMessageWriter()
        // A surrogate pair must survive as one code point, not be replaced by the decoder.
        val body = "\uD83D\uDE80 rocket"
        val fixture = cliFixture(messageWriter = writer, sendCommand = sendWithUtf8(body))

        fixture.run("send", "@ada", "-")

        val sent = writer.sends.single().text
        assertEquals(body, sent)
        // One emoji, one space, six letters: eight code points, nine UTF-16 chars.
        assertEquals(8, sent.codePointCount(0, sent.length))
        assertTrue(!sent.contains('?'), "the decoder replaced a character: $sent")
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
