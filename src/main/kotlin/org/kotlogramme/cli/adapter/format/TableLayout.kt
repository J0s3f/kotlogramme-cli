package org.kotlogramme.cli.adapter.format

import org.jline.utils.WCWidth

/** The narrowest a column is squeezed to before the table is allowed to overflow the terminal. */
internal const val MIN_COLUMN_WIDTH = 6

/** The columns a table spends on borders and padding: `| ` before each cell and ` ` after, plus the last `|`. */
private fun borderWidth(columnCount: Int): Int = 3 * columnCount + 1

/**
 * The lines of an ASCII table, laid out for [maxWidth] terminal columns.
 *
 * Without a [maxWidth] every column is as wide as its widest cell, which is how a table renders into
 * a pipe or a file. With one, the widest columns are narrowed until the table fits and the text of
 * their cells is wrapped onto extra lines, so a long cell never pushes the borders past the edge of
 * the terminal and makes every line of the table wrap. A cell may carry ANSI styling; escapes take no
 * room and a wrapped cell keeps its styling on every line it occupies.
 */
internal fun renderAsciiTable(headers: List<String>, rows: List<List<String>>, maxWidth: Int?): List<String> {
    val columnCount = maxOf(headers.size, rows.maxOfOrNull(List<String>::size) ?: 0)
    val grid = (listOf(headers) + rows).map { row -> List(columnCount) { column -> row.getOrElse(column) { "" } } }
    val natural = List(columnCount) { column -> grid.maxOf { row -> widestLine(row[column]) } }
    val widths = if (maxWidth == null) {
        natural
    } else {
        val floors = List(columnCount) { column ->
            minOf(natural[column], maxOf(MIN_COLUMN_WIDTH, widestLine(headers.getOrElse(column) { "" })))
        }
        fitColumns(natural, floors, maxWidth)
    }
    val border = widths.joinToString(separator = "+", prefix = "+", postfix = "+") { "-".repeat(it + 2) }
    return buildList {
        add(border)
        addAll(physicalLines(grid.first(), widths))
        add(border)
        grid.drop(1).forEach { addAll(physicalLines(it, widths)) }
        add(border)
    }
}

private fun widestLine(cell: String): Int = cell.split('\n').maxOf(::visibleLength)

private fun physicalLines(row: List<String>, widths: List<Int>): List<String> {
    val wrapped = row.mapIndexed { column, cell -> wrapCell(cell, widths[column]) }
    val height = wrapped.maxOfOrNull(List<String>::size) ?: 1
    return List(height) { index ->
        widths.indices.joinToString(separator = "|", prefix = "|", postfix = "|") { column ->
            val text = wrapped[column].getOrElse(index) { "" }
            " " + text + " ".repeat((widths[column] - visibleLength(text)).coerceAtLeast(0)) + " "
        }
    }
}

/**
 * The width of each column so that the table fits in [available] terminal columns.
 *
 * Columns narrower than a cap keep their natural width and every wider one is cut to the cap, so the
 * widest columns give way first and narrow ones such as ids are left alone. The cap is the largest
 * that fits, and any columns left over are handed out one at a time to the capped columns, so the
 * table uses the whole width. No column goes below its floor, which keeps a header on one line; when
 * the floors alone do not fit, the floor drops to [MIN_COLUMN_WIDTH]; and when even that does not fit,
 * the table is returned at that size and overflows, because a narrower table cannot be read.
 */
internal fun fitColumns(natural: List<Int>, floors: List<Int>, available: Int): List<Int> {
    if (natural.isEmpty()) return natural
    val budget = available - borderWidth(natural.size)
    if (natural.sum() <= budget) return natural

    val lows = if (floors.sum() <= budget) floors else natural.map { minOf(it, MIN_COLUMN_WIDTH) }
    if (lows.sum() >= budget) return lows

    fun widthsFor(cap: Int) = natural.indices.map { maxOf(lows[it], minOf(natural[it], cap)) }
    var low = 0
    var high = natural.max()
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (widthsFor(middle).sum() <= budget) low = middle else high = middle - 1
    }
    val widths = widthsFor(low).toMutableList()
    var spare = budget - widths.sum()
    while (spare > 0) {
        val cut = widths.indices.filter { widths[it] < natural[it] && widths[it] == low }
        if (cut.isEmpty()) break
        for (column in cut) {
            if (spare == 0) break
            widths[column]++
            spare--
        }
    }
    return widths
}

/**
 * [text] broken into lines of at most [width] display columns.
 *
 * Text that already fits and has no line break comes back untouched, so a table that fits renders as
 * it always did. Otherwise lines break at spaces, a word longer than a line is cut at the edge, and
 * the cut falls on a grapheme boundary, so a flag or a ZWJ emoji is never split. The width is counted
 * as [visibleLength] counts it: wide characters take two columns and escapes none. A style that is
 * open where a line ends is closed there and opened again at the start of the next line, so it
 * neither leaks into the neighbouring cell nor stops halfway down the wrapped text.
 */
internal fun wrapCell(text: String, width: Int): List<String> {
    val limit = width.coerceAtLeast(1)
    if ('\n' !in text && visibleLength(text) <= limit) return listOf(text)
    return LineBreaker(limit).lay(atomsOf(text.replace("\r", "")))
}

private enum class AtomKind { TEXT, SPACE, STYLE, NEWLINE }

private class Atom(val text: String, val width: Int, val kind: AtomKind)

private fun atomsOf(text: String): List<Atom> {
    val atoms = mutableListOf<Atom>()
    var index = 0
    while (index < text.length) {
        val escape = SGR_SEQUENCE.matchAt(text, index)
        if (escape != null) {
            atoms += Atom(escape.value, 0, AtomKind.STYLE)
            index += escape.value.length
            continue
        }
        if (text[index] == '\n') {
            atoms += Atom("\n", 0, AtomKind.NEWLINE)
            index++
            continue
        }
        val length = WCWidth.charCountForGraphemeCluster(text, index).coerceAtLeast(1)
        val cluster = text.substring(index, index + length)
        val kind = if (cluster == " ") AtomKind.SPACE else AtomKind.TEXT
        atoms += Atom(cluster, WCWidth.wcwidthForGraphemeCluster(text, index).coerceAtLeast(0), kind)
        index += length
    }
    return atoms
}

private val SGR_SEQUENCE = Regex("\u001B\\[[0-9;]*m")
private const val STYLE_RESET = "\u001B[0m"

/** Fills lines greedily, breaking at spaces, and carries the open style from one line to the next. */
private class LineBreaker(private val limit: Int) {
    private val lines = mutableListOf<String>()
    private var line = mutableListOf<Atom>()
    private var lineWidth = 0
    private var openStyle: List<String> = emptyList()
    private var continuation = false

    fun lay(atoms: List<Atom>): List<String> {
        for (atom in atoms) {
            when (atom.kind) {
                AtomKind.NEWLINE -> endParagraph()
                AtomKind.STYLE -> line += atom
                AtomKind.SPACE, AtomKind.TEXT -> place(atom)
            }
        }
        emit(line)
        return lines
    }

    private fun place(atom: Atom) {
        if (atom.kind == AtomKind.SPACE && continuation && lineWidth == 0) return
        while (lineWidth > 0 && lineWidth + atom.width > limit) {
            if (atom.kind == AtomKind.SPACE) {
                softBreak(line.size, line.size)
                return
            }
            val lastSpace = line.indexOfLast { it.kind == AtomKind.SPACE }
            if (lastSpace >= 0 && line.take(lastSpace).any { it.kind == AtomKind.TEXT }) {
                softBreak(lastSpace, lastSpace + 1)
            } else {
                softBreak(line.size, line.size)
            }
        }
        line += atom
        lineWidth += atom.width
    }

    /** Ends the line before [head] and starts the next one with what follows [tailStart]. */
    private fun softBreak(head: Int, tailStart: Int) {
        val tail = line.drop(tailStart)
        emit(line.take(head).dropLastWhile { it.kind == AtomKind.SPACE })
        line = tail.toMutableList()
        lineWidth = tail.sumOf { it.width }
        continuation = true
    }

    private fun endParagraph() {
        emit(line)
        line = mutableListOf()
        lineWidth = 0
        continuation = false
    }

    private fun emit(atoms: List<Atom>) {
        val ending = styleAfter(openStyle, atoms)
        val prefix = openStyle.joinToString("")
        val suffix = if (ending.isEmpty()) "" else STYLE_RESET
        lines += prefix + atoms.joinToString("") { it.text } + suffix
        openStyle = ending
    }

    private fun styleAfter(start: List<String>, atoms: List<Atom>): List<String> =
        atoms.filter { it.kind == AtomKind.STYLE }.fold(start) { open, escape -> open.applying(escape.text) }

    /** The styles open after [escape]: `ESC[0m` and `ESC[m` close everything, any other code is added. */
    private fun List<String>.applying(escape: String): List<String> {
        val codes = escape.removePrefix("\u001B[").removeSuffix("m").split(';')
        if (codes.first() != "" && codes.first() != "0") return this + escape
        val rest = codes.drop(1)
        return if (rest.isEmpty()) emptyList() else listOf("\u001B[" + rest.joinToString(";") + "m")
    }
}
