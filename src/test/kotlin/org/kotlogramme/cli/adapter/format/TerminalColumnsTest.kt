package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalColumnsTest {
    @Test
    fun `a terminal that cannot be opened has no width`() {
        assertNull(TerminalColumns(open = { error("no terminal") }).get())
    }

    @Test
    fun `the terminal is not opened until a width is asked for`() {
        var opened = false

        TerminalColumns(open = { opened = true; error("no terminal") })

        assertTrue(!opened)
    }

    @Test
    fun `the real terminal has either no width or a positive one and never throws`() {
        val columns = TerminalColumns().get()

        assertTrue(columns == null || columns > 0, "columns: $columns")
    }

    @Test
    fun `asking twice gives the same answer on an unchanged terminal`() {
        val terminal = TerminalColumns()

        assertTrue(terminal.get() == terminal.get())
    }
}
