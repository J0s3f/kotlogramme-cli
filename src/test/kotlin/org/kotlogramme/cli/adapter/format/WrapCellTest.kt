package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrapCellTest {
    private val esc = "\u001B"
    private val reset = "$esc[0m"
    private val red = "$esc[31m"
    private val bold = "$esc[1m"

    @Test
    fun `text that fits comes back untouched`() {
        assertEquals(listOf("hello world"), wrapCell("hello world", 11))
    }

    @Test
    fun `text that fits keeps its inner spacing`() {
        assertEquals(listOf("a  b"), wrapCell("a  b", 10))
    }

    @Test
    fun `an empty cell is one empty line`() {
        assertEquals(listOf(""), wrapCell("", 10))
    }

    @Test
    fun `breaks at a space`() {
        assertEquals(listOf("the quick", "brown fox"), wrapCell("the quick brown fox", 10))
    }

    @Test
    fun `breaks only when the text is one column too wide`() {
        assertEquals(listOf("abcde fghij"), wrapCell("abcde fghij", 11))
        assertEquals(listOf("abcde", "fghij"), wrapCell("abcde fghij", 10))
    }

    @Test
    fun `cuts a word longer than the line at the edge`() {
        assertEquals(listOf("abcd", "efgh", "ij"), wrapCell("abcdefghij", 4))
    }

    @Test
    fun `cuts a long word that follows short ones`() {
        assertEquals(listOf("ab", "cdef", "ghij"), wrapCell("ab cdefghij", 4))
    }

    @Test
    fun `breaks a path after its separators`() {
        val path = "C:\\very\\long\\path\\name"

        assertEquals(listOf("C:\\very\\", "long\\", "path\\", "name"), wrapCell(path, 8))
    }

    @Test
    fun `breaks a path after a slash`() {
        assertEquals(listOf("/usr/local/", "share/doc"), wrapCell("/usr/local/share/doc", 11))
    }

    @Test
    fun `cuts a segment that is wider than the line`() {
        assertEquals(listOf("/ab/", "cdef", "ghij"), wrapCell("/ab/cdefghij", 4))
    }

    @Test
    fun `prefers a space to a separator`() {
        assertEquals(listOf("see", "/usr/local/bin"), wrapCell("see /usr/local/bin", 14))
    }

    @Test
    fun `breaks after a hyphen or an underscore when there is no space`() {
        assertEquals(listOf("well-", "known"), wrapCell("well-known", 7))
        assertEquals(listOf("user_", "name"), wrapCell("user_name", 6))
    }

    @Test
    fun `breaks between words before cutting a word`() {
        assertEquals(listOf("one two", "three four"), wrapCell("one two three four", 10))
    }

    @Test
    fun `drops the spaces a break falls on`() {
        assertEquals(listOf("aaaa", "bbbb"), wrapCell("aaaa  bbbb", 4))
    }

    @Test
    fun `trims trailing spaces from a line that was broken`() {
        assertEquals(listOf("ab", "cd"), wrapCell("ab  cd", 3))
    }

    @Test
    fun `a line break in the text starts a new line`() {
        assertEquals(listOf("one", "two"), wrapCell("one\ntwo", 10))
    }

    @Test
    fun `an empty line in the text is kept`() {
        assertEquals(listOf("a", "", "b"), wrapCell("a\n\nb", 10))
    }

    @Test
    fun `lines in the text wrap on their own`() {
        assertEquals(listOf("aaa", "bbb", "ccc"), wrapCell("aaa bbb\nccc", 3))
    }

    @Test
    fun `a carriage return before a line break is ignored`() {
        assertEquals(listOf("one", "two"), wrapCell("one\r\ntwo", 10))
    }

    @Test
    fun `a width of one still makes progress`() {
        assertEquals(listOf("a", "b", "c"), wrapCell("abc", 1))
    }

    @Test
    fun `a width of zero is treated as one`() {
        assertEquals(listOf("a", "b"), wrapCell("ab", 0))
    }

    @Test
    fun `a character wider than the line gets a line of its own`() {
        assertEquals(listOf("日"), wrapCell("日", 1))
    }

    @Test
    fun `text of only spaces does not hang or overflow`() {
        val lines = wrapCell("     ", 2)

        assertTrue(lines.all { visibleLength(it) <= 2 }, lines.toString())
    }

    @Test
    fun `wide characters count as two columns`() {
        assertEquals(listOf("日本語", "日本語"), wrapCell("日本語日本語", 6))
    }

    @Test
    fun `a wide character that does not fit moves to the next line`() {
        assertEquals(listOf("ab", "日", "本"), wrapCell("ab日本", 3))
    }

    @Test
    fun `an emoji sequence is never split`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"

        val lines = wrapCell("ab${family}cd", 4)

        assertEquals(listOf("ab$family", "cd"), lines)
    }

    @Test
    fun `a flag is never split`() {
        val flag = "\uD83C\uDDE6\uD83C\uDDF9"

        val lines = wrapCell("x${flag}y${flag}z", 3)

        assertTrue(lines.all { flag in it || it.none { char -> char.isSurrogate() } }, lines.toString())
        assertEquals(2, lines.joinToString("").split(flag).size - 1)
    }

    @Test
    fun `no line is wider than the width for any width`() {
        val text = "The quick brown fox jumps over the lazy dog, then keeps running through pathnames/like/this/one"

        for (width in 1..40) {
            val lines = wrapCell(text, width)

            assertTrue(lines.all { visibleLength(it) <= width }, "width $width: $lines")
        }
    }

    @Test
    fun `wrapping keeps every character`() {
        val text = "The quick brown fox jumps over the lazy dog, then keeps running through pathnames/like/this/one"

        for (width in 1..40) {
            val kept = wrapCell(text, width).joinToString("").replace(" ", "")

            assertEquals(text.replace(" ", ""), kept, "width $width")
        }
    }

    @Test
    fun `once no word is cut a wider width never makes more lines`() {
        val text = "The quick brown fox jumps over the lazy dog, then keeps running"
        val longestWord = text.split(" ").maxOf(String::length)
        var previous = Int.MAX_VALUE

        for (width in longestWord..70) {
            val count = wrapCell(text, width).size

            assertTrue(count <= previous, "width $width: $count lines after $previous")
            previous = count
        }
    }

    @Test
    fun `there are never fewer lines than the text needs`() {
        val text = "The quick brown fox jumps over the lazy dog, then keeps running"
        val letters = text.count { it != ' ' }

        for (width in 1..70) {
            val needed = (letters + width - 1) / width

            assertTrue(wrapCell(text, width).size >= needed, "width $width")
        }
    }

    @Test
    fun `styled text that fits is untouched`() {
        assertEquals(listOf("${bold}bold$reset"), wrapCell("${bold}bold$reset", 4))
    }

    @Test
    fun `escapes take no room when deciding whether to wrap`() {
        assertEquals(listOf("$red${bold}abcd$reset"), wrapCell("$red${bold}abcd$reset", 4))
    }

    @Test
    fun `a style open at a break is closed there and reopened on the next line`() {
        assertEquals(listOf("${red}red$reset", "${red}fox$reset"), wrapCell("${red}red fox$reset", 3))
    }

    @Test
    fun `a style that was closed before the break is not carried over`() {
        assertEquals(listOf("${bold}ab$reset cd", "ef"), wrapCell("${bold}ab$reset cd ef", 5))
    }

    @Test
    fun `two open styles are both carried over`() {
        val lines = wrapCell("$red${bold}aa bb$reset", 2)

        assertEquals(listOf("$red${bold}aa$reset", "$red${bold}bb$reset"), lines)
    }

    @Test
    fun `a style carried over a hard cut`() {
        assertEquals(listOf("${red}abc$reset", "${red}def$reset"), wrapCell("${red}abcdef$reset", 3))
    }

    @Test
    fun `every wrapped styled line is within the width`() {
        val text = "${red}the quick brown fox$reset jumps ${bold}over the lazy dog$reset"

        for (width in 1..30) {
            assertTrue(wrapCell(text, width).all { visibleLength(it) <= width }, "width $width")
        }
    }

    @Test
    fun `no style stays open at the end of a wrapped cell`() {
        val text = "${red}the quick brown fox jumps over the lazy dog$reset"

        val lines = wrapCell(text, 10)

        assertTrue(lines.all { it.endsWith(reset) }, lines.toString())
    }

    @Test
    fun `an escape that sets then resets in one code closes the style`() {
        val lines = wrapCell("$esc[0;1mab cd$reset", 2)

        assertEquals(listOf("$esc[0;1mab$reset", "$esc[1mcd$reset"), lines)
    }
}
