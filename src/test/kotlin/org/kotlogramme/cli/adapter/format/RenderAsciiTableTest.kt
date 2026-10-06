package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderAsciiTableTest {
    private val esc = "\u001B"
    private val reset = "$esc[0m"
    private val red = "$esc[31m"

    private val simple = listOf(
        "+----+-------+",
        "| id | name  |",
        "+----+-------+",
        "| 1  | josef |",
        "+----+-------+",
    )

    private fun table(maxWidth: Int?, headers: List<String>, vararg rows: List<String>) =
        renderAsciiTable(headers, rows.toList(), maxWidth)

    @Test
    fun `without a width every column fits its widest cell`() {
        assertEquals(simple, table(null, listOf("id", "name"), listOf("1", "josef")))
    }

    @Test
    fun `a table that fits the width renders exactly as it does without one`() {
        assertEquals(simple, table(40, listOf("id", "name"), listOf("1", "josef")))
    }

    @Test
    fun `a table that exactly fills the width is not wrapped`() {
        assertEquals(simple, table(simple.first().length, listOf("id", "name"), listOf("1", "josef")))
    }

    @Test
    fun `wraps the widest column and keeps the borders aligned`() {
        val lines = table(17, listOf("id", "text"), listOf("1", "aaa bbb ccc ddd"))

        assertEquals(
            listOf(
                "+----+----------+",
                "| id | text     |",
                "+----+----------+",
                "| 1  | aaa bbb  |",
                "|    | ccc ddd  |",
                "+----+----------+",
            ),
            lines,
        )
    }

    @Test
    fun `no line of a wrapped table is wider than the width`() {
        val rows = listOf(
            listOf("1", "Ada Lovelace", "a rather long description that keeps going well past any sensible width"),
            listOf("22", "Grace", "short"),
        )

        for (width in 30..120) {
            val lines = renderAsciiTable(listOf("id", "name", "description"), rows, width)

            assertTrue(lines.all { visibleLength(it) <= width }, "width $width: ${lines.maxOf(::visibleLength)}")
        }
    }

    @Test
    fun `every line of a wrapped table is equally wide so the borders line up`() {
        val rows = listOf(listOf("1", "Ada Lovelace", "text that needs more room than it has been given here"))

        val lines = renderAsciiTable(listOf("id", "name", "description"), rows, 50)

        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }

    @Test
    fun `a wrapped table uses the full width when it can`() {
        val rows = listOf(listOf("1", "x".repeat(200)))

        val lines = renderAsciiTable(listOf("id", "text"), rows, 60)

        assertEquals(60, visibleLength(lines.first()))
    }

    @Test
    fun `the other cells of a wrapped row are blank on its continuation lines`() {
        val lines = table(25, listOf("id", "name", "text"), listOf("7", "Ada", "one two three four five six"))

        val continuation = lines.filter { it.startsWith("|") && !it.contains("id") && !it.contains("7") }
            .drop(1)

        assertTrue(continuation.all { it.startsWith("|    |      |") }, lines.toString())
    }

    @Test
    fun `two cells of one row wrap side by side`() {
        val lines = table(25, listOf("a", "b"), listOf("aaaa bbbb cccc", "dddd eeee ffff"))

        val body = lines.drop(3).dropLast(1)

        assertTrue(body.size >= 2, lines.toString())
        assertTrue(body.all { visibleLength(it) == visibleLength(lines.first()) }, lines.toString())
        assertTrue(body.first().contains("aaaa") && body.first().contains("dddd"), lines.toString())
    }

    @Test
    fun `a header stays on one line while its column can afford it`() {
        val lines = table(40, listOf("message_id", "text"), listOf("639291", "x".repeat(100)))

        assertTrue(lines[1].contains("message_id"), lines.toString())
    }

    @Test
    fun `a narrow column is left alone while the wide one wraps`() {
        val lines = table(30, listOf("id", "text"), listOf("12345", "word ".repeat(20).trim()))

        assertTrue(lines.any { it.startsWith("| 12345 |") }, lines.toString())
    }

    @Test
    fun `rows with fewer cells than the headers are padded`() {
        val lines = table(null, listOf("a", "b", "c"), listOf("1"))

        assertEquals("| 1 |   |   |", lines[3])
    }

    @Test
    fun `a table with no rows is just its header`() {
        assertEquals(
            listOf("+----+", "| id |", "+----+", "+----+"),
            renderAsciiTable(listOf("id"), emptyList(), 40),
        )
    }

    @Test
    fun `a table with no columns does not fail`() {
        assertEquals(listOf("++", "||", "++", "++"), renderAsciiTable(emptyList(), emptyList(), 40))
    }

    @Test
    fun `a line break in a cell makes more lines without any width`() {
        val lines = table(null, listOf("text"), listOf("one\ntwo"))

        assertEquals(
            listOf("+------+", "| text |", "+------+", "| one  |", "| two  |", "+------+"),
            lines,
        )
    }

    @Test
    fun `a terminal too narrow for the table overflows instead of failing`() {
        val lines = table(5, listOf("alpha", "beta"), listOf("a long cell value", "another long cell"))

        assertTrue(lines.size > 4, lines.toString())
        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }

    @Test
    fun `styled cells line up with unstyled ones`() {
        val lines = table(30, listOf("id", "text"), listOf("1", "${red}the quick brown fox jumps over the dog$reset"))

        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }

    @Test
    fun `a style never leaks from one cell to the next`() {
        val lines = table(30, listOf("text", "other"), listOf("${red}aaaa bbbb cccc dddd eeee$reset", "plain"))

        val styled = lines.filter { it.contains(red) }

        assertTrue(styled.all { it.indexOf(reset) > it.indexOf(red) }, lines.toString())
    }

    @Test
    fun `wide characters line up too`() {
        val lines = table(24, listOf("id", "text"), listOf("1", "日本語のテキストがとても長い場合"))

        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }

    @Test
    fun `emoji line up too`() {
        val lines = table(24, listOf("id", "text"), listOf("1", "party 🎉 time 🎉 again 🎉 and again"))

        assertEquals(1, lines.map(::visibleLength).distinct().size, lines.toString())
    }

    @Test
    fun `a wrapped table keeps every word of its cells`() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa"

        val lines = table(30, listOf("id", "text"), listOf("1", text))

        val words = lines.drop(3).dropLast(1).joinToString(" ") { it.split("|")[2] }.split(Regex(" +")).filter(String::isNotEmpty)
        assertEquals(text.split(" "), words)
    }
}
