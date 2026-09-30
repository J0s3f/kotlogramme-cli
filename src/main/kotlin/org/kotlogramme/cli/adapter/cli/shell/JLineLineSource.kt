package org.kotlogramme.cli.adapter.cli.shell

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader

/** The production [LineSource]: JLine's line editor, with `null` for Ctrl-D and end of input. */
class JLineLineSource(private val reader: LineReader) : LineSource {
    override fun read(prompt: String): String? = try {
        reader.readLine(prompt)
    } catch (eof: EndOfFileException) {
        null
    }
}
