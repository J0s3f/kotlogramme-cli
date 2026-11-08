package org.kotlogramme.cli.application.port.spi

/**
 * Where a command sends the output it wants the user to see.
 *
 * Commands never print directly: they write here, which keeps them testable and lets the same
 * command render as a table, as plain text or as JSON depending on the configured format. Every
 * kind of statement has its own method, so a command never builds a line of text a program would
 * have to parse; the defaults give the text form and an implementation overrides what JSON changes.
 */
interface Output {
    fun line(text: String = "")

    /** Renders a table; [title] is optional context shown above it. */
    fun table(headers: List<String>, rows: List<List<String>>, title: String? = null)

    /** Reports what a command did or found, such as `Blocked @ada.`. */
    fun message(text: String) = line(text)

    /** Tells the reader how to continue a listing that was cut short at [cursor]. */
    fun nextPage(cursor: String) = line("# next: --after $cursor")

    /** Reports how many items matched. */
    fun count(total: Int) = line(total.toString())

    /** Reports one item as named fields, such as a saved file's path and size. */
    fun record(fields: List<Pair<String, String>>) = line(fields.joinToString("\t") { it.second })

    /** Reports one thing as a [headline] and labelled [fields], such as the signed in account. */
    fun details(headline: String, fields: List<Pair<String, String>>) {
        line(headline)
        fields.forEach { (label, value) -> line("$label: $value") }
    }

    /** Reports labelled settings; a label is human text such as `Session path`. */
    fun properties(properties: List<Pair<String, String>>) =
        properties.forEach { (label, value) -> line("$label: $value") }
}
