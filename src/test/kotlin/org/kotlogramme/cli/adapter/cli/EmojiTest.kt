package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmojiTest {
    @Test
    fun `a literal emoji is returned unchanged`() {
        assertEquals("\uD83D\uDC4D", decodeEmojiArgument("\uD83D\uDC4D"))
    }

    @Test
    fun `a code point escape becomes the emoji`() {
        assertEquals("\uD83D\uDC4D", decodeEmojiArgument("U+1F44D"))
        assertEquals("\uD83D\uDC4D", decodeEmojiArgument("u+1f44d"))
    }

    @Test
    fun `a java escape pair becomes the emoji`() {
        assertEquals("\uD83D\uDC4D", decodeEmojiArgument("\\uD83D\\uDC4D"))
    }

    @Test
    fun `a single code unit escape becomes that character`() {
        assertEquals("A", decodeEmojiArgument("\\u0041"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("\uD83D\uDC4D", decodeEmojiArgument("  U+1F44D  "))
    }

    @Test
    fun `a code point outside unicode is refused`() {
        assertFailsWith<IllegalArgumentException> { decodeEmojiArgument("U+110000") }
    }

    @Test
    fun `a malformed escape is refused`() {
        assertFailsWith<IllegalArgumentException> { decodeEmojiArgument("\\u12") }
    }
}
