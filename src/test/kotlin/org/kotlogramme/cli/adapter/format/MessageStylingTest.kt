package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.MessageEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageStylingTest {
    private fun entity(kind: String, offset: Int, length: Int) = MessageEntity(kind, offset, length)

    @Test
    fun `the terminal kinds each map to their style`() {
        val styler = MessageStyler.table(color = true)

        assertEquals("\u001B[1mbold\u001B[22m", styler.style("bold", listOf(entity("bold", 0, 4))))
        assertEquals("\u001B[3mit\u001B[23m", styler.style("it", listOf(entity("italic", 0, 2))))
        assertEquals("\u001B[4munder\u001B[24m", styler.style("under", listOf(entity("underline", 0, 5))))
        assertEquals("\u001B[9mgone\u001B[29m", styler.style("gone", listOf(entity("strike", 0, 4))))
        assertEquals("\u001B[36mcode\u001B[39m", styler.style("code", listOf(entity("code", 0, 4))))
        assertEquals("\u001B[36mpre\u001B[39m", styler.style("pre", listOf(entity("pre", 0, 3))))
        assertEquals(
            "\u001B[4;34mlink\u001B[24;39m",
            styler.style("link", listOf(entity("textUrl", 0, 4))),
        )
        assertEquals("\u001B[36m@ada\u001B[39m", styler.style("@ada", listOf(entity("mention", 0, 4))))
    }

    @Test
    fun `a kind with no terminal equivalent is left as plain text`() {
        val styler = MessageStyler.table(color = true)

        assertEquals("emoji", styler.style("emoji", listOf(entity("customEmoji", 0, 5))))
        assertEquals("quote", styler.style("quote", listOf(entity("blockquote", 0, 5))))
        assertEquals("future", styler.style("future", listOf(entity("someFutureKind", 0, 6))))
    }

    @Test
    fun `nested entities compose their styles`() {
        val styler = MessageStyler.table(color = true)

        val styled = styler.style("ab", listOf(entity("bold", 0, 2), entity("italic", 1, 1)))

        assertEquals("\u001B[1ma\u001B[3mb\u001B[23m\u001B[22m", styled)
    }

    @Test
    fun `colour off emits no escapes but still masks a spoiler`() {
        val styler = MessageStyler.table(color = false)

        assertEquals("bold spoiler", styler.style("bold spoiler", listOf(entity("bold", 0, 4))))
        val masked = styler.style("hidden", listOf(entity("spoiler", 0, 6)))
        assertEquals("\u2588".repeat(6), masked)
        assertFalse(masked.contains("\u001B"))
    }

    @Test
    fun `a spoiler is masked one block per character`() {
        val styler = MessageStyler.table(color = true)

        val styled = styler.style("say hi", listOf(entity("spoiler", 4, 2)))

        assertEquals("say \u2588\u2588", styled)
        assertFalse(styled.contains("hi"))
    }

    @Test
    fun `a span outside the text is ignored rather than throwing`() {
        val styler = MessageStyler.table(color = true)

        assertEquals("hi", styler.style("hi", listOf(entity("bold", 10, 5))))
        assertEquals("hi", styler.style("hi", listOf(entity("bold", 0, 99))))
        assertEquals("hi", styler.style("hi", listOf(entity("bold", -1, 1))))
        assertEquals("hi", styler.style("hi", listOf(entity("bold", 0, 0))))
        assertEquals("hi", styler.style("hi", listOf(entity("spoiler", 5, 2))))
    }

    @Test
    fun `the plain styler changes nothing`() {
        val styled = MessageStyler.PLAIN.style("hidden", listOf(entity("bold", 0, 6), entity("spoiler", 0, 6)))

        assertEquals("hidden", styled)
    }

    @Test
    fun `the table format styles, the plain and json formats do not`() {
        val spoiler = listOf(entity("spoiler", 0, 2))

        assertEquals("\u2588\u2588", messageStylerFor(OutputFormat.TABLE, color = false).style("hi", spoiler))
        assertEquals("hi", messageStylerFor(OutputFormat.PLAIN, color = true).style("hi", spoiler))
        assertEquals("hi", messageStylerFor(OutputFormat.JSON, color = true).style("hi", spoiler))
    }

    @Test
    fun `colour is on only with a terminal, no flag and no NO_COLOR`() {
        assertTrue(colorEnabled(noColor = false, color = false, environment = emptyMap(), terminal = true))
        assertFalse(colorEnabled(noColor = true, color = false, environment = emptyMap(), terminal = true))
        assertFalse(colorEnabled(noColor = false, color = false, environment = mapOf("NO_COLOR" to "1"), terminal = true))
        assertFalse(colorEnabled(noColor = false, color = false, environment = emptyMap(), terminal = false))
        assertTrue(colorEnabled(noColor = false, color = false, environment = mapOf("NO_COLOR" to ""), terminal = true))
    }

    @Test
    fun `the colour flag forces styling off a terminal, and no-color still wins`() {
        assertTrue(colorEnabled(noColor = false, color = true, environment = emptyMap(), terminal = false))
        assertTrue(colorEnabled(noColor = false, color = true, environment = mapOf("NO_COLOR" to "1"), terminal = false))
        assertFalse(colorEnabled(noColor = true, color = true, environment = emptyMap(), terminal = true))
    }
}
