package org.kotlogramme.cli.application.port.spi

/**
 * Where a command sends the output it wants the user to see.
 *
 * Commands never print directly: they write here, which keeps them testable and lets the same
 * command render as a table, as plain text or as JSON depending on the configured format.
 */
interface Output {
    fun line(text: String = "")

    /** Renders a table; [title] is optional context shown above it. */
    fun table(headers: List<String>, rows: List<List<String>>, title: String? = null)
}
