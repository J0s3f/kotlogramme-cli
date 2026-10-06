package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrapCellEscapeTest {
    private val esc = "\u001B"
    private val open = "$esc]8;;https://example.org$esc\\"
    private val close = "$esc]8;;$esc\\"
    private val bell = "\u0007"

    @Test
    fun `a hyperlink takes no room`() {
        assertEquals(5, visibleLength("${open}hello$close"))
    }

    @Test
    fun `a hyperlink ended by a bell takes no room`() {
        assertEquals(5, visibleLength("$esc]8;;https://example.org${bell}hello$esc]8;;$bell"))
    }

    @Test
    fun `a cursor movement takes no room`() {
        assertEquals(2, visibleLength("a$esc[2Kb"))
    }

    @Test
    fun `a linked text that fits is left alone`() {
        assertEquals(listOf("${open}hello$close"), wrapCell("${open}hello$close", 5))
    }

    @Test
    fun `a wrapped link is closed and opened again on every line`() {
        val lines = wrapCell("${open}one two three$close", 7)

        assertEquals(listOf("${open}one two$close", "${open}three$close"), lines)
    }

    @Test
    fun `text outside a link is not linked`() {
        val lines = wrapCell("aaa ${open}bbb ccc$close ddd", 7)

        assertEquals(listOf("aaa ${open}bbb$close", "${open}ccc$close ddd"), lines)
    }

    @Test
    fun `a link and a style wrap together`() {
        val red = "$esc[31m"
        val reset = "$esc[0m"

        val lines = wrapCell("$red${open}one two$close$reset", 3)

        assertEquals(listOf("$red${open}one$close$reset", "$red${open}two$close$reset"), lines)
    }

    @Test
    fun `a link that is never closed is closed at the end of its line`() {
        val lines = wrapCell("${open}one two", 3)

        assertTrue(lines.all { it.endsWith(close) }, lines.toString())
    }

    @Test
    fun `other escapes pass through where they were`() {
        assertEquals(listOf("a${esc}[2Kb", "c"), wrapCell("a${esc}[2Kb c", 2))
    }

    @Test
    fun `a wrapped table with links lines up`() {
        val lines = renderAsciiTable(listOf("id", "text"), listOf(listOf("1", "${open}one two three four$close")), 18)

        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }
}
