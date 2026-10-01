package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeDownloadMedia
import org.kotlogramme.cli.adapter.cli.FakeInlineBots
import org.kotlogramme.cli.adapter.cli.FakeListDialogs
import org.kotlogramme.cli.adapter.cli.FakeListFolders
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeReadHistory
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.FakeSendMedia
import org.kotlogramme.cli.adapter.cli.FakeStickers
import org.kotlogramme.cli.adapter.cli.RecordingOutput
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

    @Test
    fun `every completed command is one the shell dispatches`() {
        // The completer, HELP and dispatch() are kept in sync by hand; this catches a verb offered by
        // completion that dispatch() would reject as unknown. Driving the shell is what proves it.
        val offered = ShellCompleter { emptyList() }.completeCommands()

        val unknown = offered.filter { verb ->
            val output = RecordingOutput()
            Shell(ScriptedLines(listOf(verb)), output, fakeUseCases()).run()
            output.text.contains("Unknown command: $verb")
        }

        assertEquals(emptyList(), unknown)
    }

    private class ScriptedLines(private val lines: List<String>) : LineSource {
        private var index = 0
        override fun read(prompt: String): String? = lines.getOrNull(index++)
    }

    private fun fakeUseCases() = ShellUseCases(
        { FakeListDialogs() }, { FakeReadHistory() }, { FakeMessageWriter() }, { FakeContacts() },
        { FakeSearchMessages() }, { FakeChatMembers() }, { FakeListFolders() }, { FakeStickers() },
        { FakeInlineBots() }, { FakeSendMedia() }, { FakeDownloadMedia() },
    )
}
