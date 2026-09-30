package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.FakeInlineBots
import org.kotlogramme.cli.adapter.cli.FakeListDialogs
import org.kotlogramme.cli.adapter.cli.FakeListFolders
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeReadHistory
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.FakeStickers
import org.kotlogramme.cli.adapter.cli.InlineQueryCall
import org.kotlogramme.cli.adapter.cli.InlineSendCall
import org.kotlogramme.cli.adapter.cli.MemberCall
import org.kotlogramme.cli.adapter.cli.MissingCredentialsError
import org.kotlogramme.cli.adapter.cli.RecordingOutput
import org.kotlogramme.cli.adapter.cli.SendCall
import org.kotlogramme.cli.adapter.cli.StickerSendCall
import org.kotlogramme.cli.adapter.cli.inlineResult
import org.kotlogramme.cli.adapter.cli.testStickerSet
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.Folder
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Participant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellTest {
    private val ada = Chat(
        id = 1,
        title = "Ada",
        kind = ChatKind.PRIVATE,
        username = "ada",
        lastMessagePreview = "hi",
        lastMessageAt = null,
    )

    /** Replays a fixed list of lines, then reports end of input; records the prompts it was given. */
    private class ScriptedLineSource(private val lines: List<String>) : LineSource {
        val prompts = mutableListOf<String>()
        private var index = 0

        override fun read(prompt: String): String? {
            prompts += prompt
            return lines.getOrNull(index++)
        }
    }

    private fun fakeUseCases(
        dialogs: ListDialogs = FakeListDialogs(listOf(ada)),
        history: ReadHistory = FakeReadHistory(),
        writer: MessageWriter = FakeMessageWriter(),
        contacts: Contacts = FakeContacts(),
        search: SearchMessages = FakeSearchMessages(),
        members: ChatMembers = FakeChatMembers(),
        folders: ListFolders = FakeListFolders(),
        stickers: Stickers = FakeStickers(),
        inline: InlineBots = FakeInlineBots(),
    ) = ShellUseCases(
        { dialogs }, { history }, { writer }, { contacts }, { search },
        { members }, { folders }, { stickers }, { inline },
    )

    private fun run(
        lines: List<String>,
        useCases: ShellUseCases = fakeUseCases(),
        output: RecordingOutput = RecordingOutput(),
    ): RecordingOutput {
        Shell(ScriptedLineSource(lines), output, useCases).run()
        return output
    }

    @Test
    fun `open selects the chat and send writes to it`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "send hello there"), fakeUseCases(writer = writer))

        assertEquals(SendCall("@ada", "hello there", null, false), writer.sends.single())
    }

    @Test
    fun `the prompt reflects the current chat`() {
        val lines = ScriptedLineSource(listOf("open @ada"))

        Shell(lines, RecordingOutput(), fakeUseCases()).run()

        assertEquals(listOf("> ", "[Ada]> "), lines.prompts)
    }

    @Test
    fun `reply targets the remembered chat with the message id`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "reply 7 hello again"), fakeUseCases(writer = writer))

        assertEquals(SendCall("@ada", "hello again", 7, false), writer.sends.single())
    }

    @Test
    fun `an unknown command is reported and the shell keeps going`() {
        val output = run(listOf("frobnicate", "help"))

        assertTrue(output.text.contains("Unknown command: frobnicate"))
        assertTrue(output.text.contains("open <peer>"))
    }

    @Test
    fun `help lists the commands`() {
        val output = run(listOf("help"))

        assertTrue(output.text.contains("dialogs | list"))
        assertTrue(output.text.contains("quit | exit"))
    }

    @Test
    fun `the shell stops at end of input`() {
        val lines = ScriptedLineSource(listOf("dialogs"))

        Shell(lines, RecordingOutput(), fakeUseCases()).run()

        assertEquals(2, lines.prompts.size)
    }

    @Test
    fun `a failing use case is reported as a message`() {
        val useCases = ShellUseCases(
            listDialogs = { throw MissingCredentialsError() },
            readHistory = { FakeReadHistory() },
            messageWriter = { FakeMessageWriter() },
            contacts = { FakeContacts() },
            searchMessages = { FakeSearchMessages() },
            chatMembers = { FakeChatMembers() },
            listFolders = { FakeListFolders() },
            stickers = { FakeStickers() },
            inline = { FakeInlineBots() },
        )

        val output = run(listOf("list"), useCases = useCases)

        assertTrue(output.text.contains("Telegram API credentials are not set"))
    }

    @Test
    fun `read keeps the previous messages as context`() {
        val history = FakeReadHistory(listOf(message(2, "two"), message(1, "one")))
        val writer = FakeMessageWriter(sent = message(3, "new", outgoing = true))

        val output = run(
            listOf("open @ada", "read", "send new", "read"),
            fakeUseCases(history = history, writer = writer),
        )

        assertEquals(
            listOf(
                SHELL_WELCOME,
                "Opened Ada.",
                MESSAGE_ROW_HEADER,
                row(1, "one"),
                row(2, "two"),
                MESSAGE_ROW_HEADER,
                row(3, "new", outgoing = true),
                MESSAGE_ROW_HEADER,
                row(1, "one"),
                row(2, "two"),
                row(3, "new", outgoing = true),
            ),
            output.lines,
        )
    }

    @Test
    fun `stickers lists the installed sets`() {
        val stickers = FakeStickers(sets = listOf(testStickerSet()))

        val output = run(listOf("stickers"), fakeUseCases(stickers = stickers))

        val rows = output.lines.filter { it.contains("cats") }
        assertTrue(rows.any { it.contains("Cats") }, output.text)
    }

    @Test
    fun `sticker-set numbers the stickers for send-sticker`() {
        val stickers = FakeStickers(setAnswer = testStickerSet())

        val output = run(listOf("sticker-set cats"), fakeUseCases(stickers = stickers))

        assertEquals(listOf("cats"), stickers.setRequests)
        val rows = output.lines.filter { it.startsWith("0\t") || it.startsWith("1\t") }
        assertEquals(listOf("0\t11\t🐱", "1\t22\t🐱"), rows)
    }

    @Test
    fun `send-sticker sends into the current chat`() {
        val stickers = FakeStickers()

        run(listOf("open @ada", "send-sticker cats 1"), fakeUseCases(stickers = stickers))

        assertEquals(StickerSendCall("@ada", "cats", 1, null, false), stickers.sends.single())
    }

    @Test
    fun `send-sticker needs an open chat`() {
        val stickers = FakeStickers()

        val output = run(listOf("send-sticker cats 0"), fakeUseCases(stickers = stickers))

        assertEquals(emptyList(), stickers.sends)
        assertTrue(output.text.contains("No chat open"))
    }

    @Test
    fun `inline sends the chosen result into the current chat`() {
        val answer = InlineQuery(
            queryId = 5L,
            results = listOf(inlineResult("a", "First"), inlineResult("b", "Second")),
        )
        val inline = FakeInlineBots(answer = answer)

        run(listOf("open @ada", "inline @gif cats --send 1"), fakeUseCases(inline = inline))

        assertEquals(InlineQueryCall("@gif", "cats", "@ada"), inline.queries.single())
        assertEquals(InlineSendCall("@ada", 5L, "b"), inline.sends.single())
    }

    @Test
    fun `inline without --send only queries the current chat`() {
        val inline = FakeInlineBots(
            answer = InlineQuery(queryId = 5L, results = listOf(inlineResult("a", "First"))),
        )

        run(listOf("open @ada", "inline @gif cats"), fakeUseCases(inline = inline))

        assertEquals(InlineQueryCall("@gif", "cats", "@ada"), inline.queries.single())
        assertEquals(emptyList(), inline.sends)
    }

    @Test
    fun `inline --send needs an open chat`() {
        val inline = FakeInlineBots()

        val output = run(listOf("inline @gif cats --send 0"), fakeUseCases(inline = inline))

        assertEquals(emptyList(), inline.queries)
        assertEquals(emptyList(), inline.sends)
        assertTrue(output.text.contains("No chat open"))
    }

    @Test
    fun `inline reports an out-of-range result index`() {
        val inline = FakeInlineBots(
            answer = InlineQuery(queryId = 5L, results = listOf(inlineResult("a", "First"))),
        )

        val output = run(listOf("open @ada", "inline @gif cats --send 4"), fakeUseCases(inline = inline))

        assertEquals(emptyList(), inline.sends)
        assertTrue(output.text.contains("No result at index 4"))
    }

    @Test
    fun `members defaults to the current chat`() {
        val members = FakeChatMembers(
            listOf(Participant(id = 9, displayName = "Ada", username = "ada", role = "member")),
        )

        run(listOf("open @ada", "members"), fakeUseCases(members = members))

        assertEquals(MemberCall("@ada", 50), members.lists.single())
    }

    @Test
    fun `members accepts an explicit peer`() {
        val members = FakeChatMembers()

        run(listOf("members @club"), fakeUseCases(members = members))

        assertEquals(MemberCall("@club", 50), members.lists.single())
    }

    @Test
    fun `folders lists the dialog folders`() {
        val folders = FakeListFolders(listOf(Folder(id = 1, title = "Work", kind = "filter")))

        val output = run(listOf("folders"), fakeUseCases(folders = folders))

        assertTrue(output.text.contains("Work"), output.text)
    }

    private fun message(id: Int, text: String, outgoing: Boolean = false) = Message(
        id = id,
        senderName = if (outgoing) "You" else "Ada",
        text = text,
        sentAt = Instant.parse("2026-01-01T00:00:00Z"),
        outgoing = outgoing,
    )

    private fun row(id: Int, text: String, outgoing: Boolean = false) =
        "$id\t2026-01-01T00:00:00Z\t${if (outgoing) "You" else "Ada"}\t\t\t\t$text"

    private companion object {
        const val MESSAGE_ROW_HEADER = "id\ttime\tfrom\treply\tmedia\taction\ttext"
    }
}
