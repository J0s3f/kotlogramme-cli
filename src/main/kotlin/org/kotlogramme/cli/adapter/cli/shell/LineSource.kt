package org.kotlogramme.cli.adapter.cli.shell

/**
 * The shell's only terminal seam: read one line, or `null` at end of input.
 *
 * Production implements it with JLine; tests replay a scripted list, which is what lets the whole
 * shell run without a terminal.
 */
interface LineSource {
    fun read(prompt: String): String?
}
