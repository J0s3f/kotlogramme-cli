package org.kotlogramme.cli.adapter.cli.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellCompleterTest {
    @Test
    fun `command names are completed from the first word`() {
        val completer = ShellCompleter { emptyList() }

        assertEquals(listOf("help"), completer.candidatesFor(listOf("he"), wordIndex = 0, word = "he"))
    }

    @Test
    fun `an empty first word offers every command`() {
        val completer = ShellCompleter { emptyList() }

        val offered = completer.candidatesFor(emptyList(), wordIndex = 0, word = "")

        assertTrue(offered.contains("help"))
        assertTrue(offered.contains("exit"))
    }

    @Test
    fun `peer references are completed after open`() {
        val completer = ShellCompleter { listOf("@ada", "@bob") }

        assertEquals(
            listOf("@ada"),
            completer.candidatesFor(listOf("open", "@a"), wordIndex = 1, word = "@a"),
        )
    }

    @Test
    fun `peers are only offered after open`() {
        val completer = ShellCompleter { listOf("@ada") }

        assertEquals(emptyList(), completer.candidatesFor(listOf("send", "hi"), wordIndex = 1, word = "hi"))
    }
}
