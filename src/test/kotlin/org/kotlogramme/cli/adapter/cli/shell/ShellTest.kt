package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.adapter.cli.DownloadMediaCall
import org.kotlogramme.cli.adapter.cli.DeleteCall
import org.kotlogramme.cli.adapter.cli.EditCall
import org.kotlogramme.cli.adapter.cli.ChatActionCall
import org.kotlogramme.cli.adapter.cli.FakeChatMembers
import org.kotlogramme.cli.adapter.cli.FakeContacts
import org.kotlogramme.cli.adapter.cli.HistoryCall
import org.kotlogramme.cli.adapter.cli.FakeDownloadMedia
import org.kotlogramme.cli.adapter.cli.FakeInlineBots
import org.kotlogramme.cli.adapter.cli.FakeListDialogs
import org.kotlogramme.cli.adapter.cli.FakeListFolders
import org.kotlogramme.cli.adapter.cli.FakeMessageWriter
import org.kotlogramme.cli.adapter.cli.FakeReadHistory
import org.kotlogramme.cli.adapter.cli.FakeSearchMessages
import org.kotlogramme.cli.adapter.cli.FakeSendMedia
import org.kotlogramme.cli.adapter.cli.FakeSessions
import org.kotlogramme.cli.adapter.cli.FakeStickers
import org.kotlogramme.cli.adapter.cli.FileSearchCall
import org.kotlogramme.cli.adapter.cli.ForwardCall
import org.kotlogramme.cli.adapter.cli.InlineQueryCall
import org.kotlogramme.cli.adapter.cli.InlineSendCall
import org.kotlogramme.cli.adapter.cli.MemberCall
import org.kotlogramme.cli.adapter.cli.MessageIdCall
import org.kotlogramme.cli.adapter.cli.MissingCredentialsError
import org.kotlogramme.cli.adapter.cli.ReactCall
import org.kotlogramme.cli.adapter.cli.RecordingOutput
import org.kotlogramme.cli.adapter.cli.SendCall
import org.kotlogramme.cli.adapter.cli.SendUrlCall
import org.kotlogramme.cli.adapter.cli.CopyMediaCall
import org.kotlogramme.cli.adapter.cli.StickerSendCall
import org.kotlogramme.cli.adapter.cli.inlineResult
import org.kotlogramme.cli.adapter.cli.testStickerSet
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatActivity
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.Folder
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Participant
import org.kotlogramme.cli.domain.Session
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        sendMedia: SendMedia = FakeSendMedia(),
        downloadMedia: DownloadMedia = FakeDownloadMedia(),
        sessions: Sessions = FakeSessions(),
    ) = ShellUseCases(
        { dialogs }, { history }, { writer }, { contacts }, { search },
        { members }, { folders }, { stickers }, { inline }, { sendMedia }, { downloadMedia }, { sessions },
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
    fun `open accepts me and @me for the chat with yourself`() {
        val saved = Chat(
            id = 42,
            title = "Saved Messages",
            kind = ChatKind.PRIVATE,
            username = null,
            lastMessagePreview = null,
            lastMessageAt = null,
            isSelf = true,
        )
        val dialogs = FakeListDialogs(listOf(saved, ada))

        val bare = run(listOf("open me"), fakeUseCases(dialogs = dialogs))
        val at = run(listOf("open @me"), fakeUseCases(dialogs = dialogs))

        assertTrue(bare.text.contains("Opened Saved Messages."), bare.text)
        assertTrue(at.text.contains("Opened Saved Messages."), at.text)
    }

    @Test
    fun `open still reports a peer that matches nothing`() {
        val output = run(listOf("open nobody"), fakeUseCases())

        assertTrue(output.text.contains("No chat matches 'nobody'."), output.text)
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
    fun `help for a verb shows its usage and its options`() {
        val output = run(listOf("help files"))

        assertTrue(output.text.contains("files [<peer>] [--kind <kind>] [--limit N]"), output.text)
        assertTrue(output.text.contains("--kind <kind>"), output.text)
        assertTrue(output.text.contains("--limit N"), output.text)
    }

    @Test
    fun `help for a verb with no options shows its usage`() {
        val output = run(listOf("help open"))

        assertTrue(output.text.contains("open <peer>"), output.text)
    }

    @Test
    fun `help for an alias answers the same as the verb it names`() {
        val viaAlias = run(listOf("help list"))
        val viaName = run(listOf("help dialogs"))

        assertTrue(viaAlias.text.contains("dialogs | list"), viaAlias.text)
        assertEquals(viaName.text, viaAlias.text)
    }

    @Test
    fun `help for a verb it does not know says so`() {
        val output = run(listOf("help frobnicate"))

        assertTrue(output.text.contains("No help for 'frobnicate'"), output.text)
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
            sendMedia = { FakeSendMedia() },
            downloadMedia = { FakeDownloadMedia() },
            sessions = { FakeSessions() },
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
    fun `read --after passes the cursor to the use case`() {
        val history = FakeReadHistory(listOf(message(1, "one")))

        run(listOf("open @ada", "read --after 5"), fakeUseCases(history = history))

        assertEquals(listOf(HistoryCall("@ada", 20, 5)), history.calls)
    }

    @Test
    fun `read prints the next cursor on a full page`() {
        val messages = (1..20).map { index -> message(index, "message $index") }
        val history = FakeReadHistory(messages)

        val output = run(listOf("open @ada", "read"), fakeUseCases(history = history))

        assertTrue(output.text.contains("# next: --after 20"), output.text)
    }

    @Test
    fun `read omits the next cursor on a short page`() {
        val history = FakeReadHistory(listOf(message(1, "one")))

        val output = run(listOf("open @ada", "read"), fakeUseCases(history = history))

        assertFalse(output.text.contains("# next:"), output.text)
    }

    @Test
    fun `read rejects a malformed --after`() {
        val history = FakeReadHistory(listOf(message(1, "one")))

        val output = run(listOf("open @ada", "read --after abc"), fakeUseCases(history = history))

        assertTrue(output.text.contains("Usage: read"), output.text)
        assertEquals(emptyList(), history.calls)
    }

    @Test
    fun `search --after passes the cursor to the use case`() {
        val search = FakeSearchMessages()

        run(listOf("open @ada", "search hello --after 7"), fakeUseCases(search = search))

        assertEquals(listOf<String?>("7"), search.searchCursors)
    }

    @Test
    fun `search prints the next cursor on a full page`() {
        val messages = (1..20).map { index -> message(index, "message $index") }
        val search = FakeSearchMessages(messages)

        val output = run(listOf("open @ada", "search hello"), fakeUseCases(search = search))

        assertTrue(output.text.contains("# next: --after 20"), output.text)
    }

    @Test
    fun `search omits the next cursor on a short page`() {
        val search = FakeSearchMessages(listOf(message(1, "one")))

        val output = run(listOf("open @ada", "search hello"), fakeUseCases(search = search))

        assertFalse(output.text.contains("# next:"), output.text)
    }

    @Test
    fun `search rejects a malformed --after`() {
        val search = FakeSearchMessages()

        val output = run(listOf("open @ada", "search hello --after abc"), fakeUseCases(search = search))

        assertTrue(output.text.contains("malformed cursor"), output.text)
        assertEquals(emptyList(), search.searches)
    }

    @Test
    fun `search --after with no value reports the usage`() {
        val search = FakeSearchMessages()

        val output = run(listOf("open @ada", "search hello --after"), fakeUseCases(search = search))

        assertTrue(output.text.contains("Usage: search"), output.text)
        assertEquals(emptyList(), search.searches)
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
    fun `members --after passes the cursor to the use case`() {
        val members = FakeChatMembers()

        run(listOf("members @club --after 10"), fakeUseCases(members = members))

        assertEquals(listOf<String?>("10"), members.cursors)
    }

    @Test
    fun `members prints the next cursor on a full page`() {
        val members = (1..50).map { index ->
            Participant(id = index.toLong(), displayName = "User $index", username = null, role = "member")
        }
        val fake = FakeChatMembers(members)

        val output = run(listOf("members @club"), fakeUseCases(members = fake))

        assertTrue(output.text.contains("# next: --after 50"), output.text)
    }

    @Test
    fun `members omits the next cursor on a short page`() {
        val members = FakeChatMembers(
            listOf(Participant(id = 1, displayName = "Ada", username = "ada", role = "member")),
        )

        val output = run(listOf("members @club"), fakeUseCases(members = members))

        assertFalse(output.text.contains("# next:"), output.text)
    }

    @Test
    fun `members rejects a malformed --after`() {
        val members = FakeChatMembers()

        val output = run(listOf("members @club --after abc"), fakeUseCases(members = members))

        assertTrue(output.text.contains("malformed cursor"), output.text)
        assertEquals(emptyList(), members.lists)
    }

    @Test
    fun `folders lists the dialog folders`() {
        val folders = FakeListFolders(listOf(Folder(id = 1, title = "Work", kind = "filter")))

        val output = run(listOf("folders"), fakeUseCases(folders = folders))

        assertTrue(output.text.contains("Work"), output.text)
    }

    @Test
    fun `files lists the current chat's files with the default kind and limit`() {
        val search = FakeSearchMessages(fileResults = listOf(fileMessage(7, "clip.mp4")))

        val output = run(listOf("open @ada", "files"), fakeUseCases(search = search))

        assertEquals(FileSearchCall("@ada", MediaFileKind.ALL, 20), search.fileSearches.single())
        assertTrue(output.text.contains("clip.mp4"), output.text)
    }

    @Test
    fun `files accepts an explicit peer, kind and limit`() {
        val search = FakeSearchMessages(fileResults = listOf(fileMessage(7, "clip.mp4")))

        run(listOf("files @club --kind video --limit 5"), fakeUseCases(search = search))

        assertEquals(FileSearchCall("@club", MediaFileKind.VIDEO, 5), search.fileSearches.single())
    }

    @Test
    fun `files --after passes the cursor to the use case`() {
        val search = FakeSearchMessages()

        run(listOf("open @ada", "files --after 9"), fakeUseCases(search = search))

        assertEquals(listOf<String?>("9"), search.fileCursors)
    }

    @Test
    fun `files prints the next cursor on a full page`() {
        val files = (1..20).map { index -> fileMessage(index, "clip$index.mp4") }
        val search = FakeSearchMessages(fileResults = files)

        val output = run(listOf("open @ada", "files"), fakeUseCases(search = search))

        assertTrue(output.text.contains("# next: --after 20"), output.text)
    }

    @Test
    fun `files omits the next cursor on a short page`() {
        val search = FakeSearchMessages(fileResults = listOf(fileMessage(7, "clip.mp4")))

        val output = run(listOf("open @ada", "files"), fakeUseCases(search = search))

        assertFalse(output.text.contains("# next:"), output.text)
    }

    @Test
    fun `files rejects a malformed --after`() {
        val search = FakeSearchMessages()

        val output = run(listOf("open @ada", "files --after abc"), fakeUseCases(search = search))

        assertTrue(output.text.contains("malformed cursor"), output.text)
        assertEquals(emptyList(), search.fileSearches)
    }

    @Test
    fun `files reports an unknown kind`() {
        val search = FakeSearchMessages()

        val output = run(listOf("open @ada", "files --kind nope"), fakeUseCases(search = search))

        assertTrue(output.text.contains("unknown file kind 'nope'"), output.text)
        assertEquals(emptyList(), search.fileSearches)
    }

    @Test
    fun `download-media saves the media and reports the path and size`() {
        val media = FakeDownloadMedia(mediaName = "cat.png")

        val output = run(listOf("download-media @ada 12"), fakeUseCases(downloadMedia = media))

        assertEquals(listOf(DownloadMediaCall("@ada", 12, Path.of("cat.png"))), media.downloads)
        assertEquals(listOf(SHELL_WELCOME, "cat.png\t64"), output.lines)
        Files.deleteIfExists(Path.of("cat.png"))
    }

    @Test
    fun `download-media accepts an explicit target`() {
        val media = FakeDownloadMedia(mediaName = "cat.png")

        val output = run(listOf("download-media @ada 12 out.bin"), fakeUseCases(downloadMedia = media))

        assertEquals(listOf(DownloadMediaCall("@ada", 12, Path.of("out.bin"))), media.downloads)
        assertEquals(listOf(SHELL_WELCOME, "out.bin\t64"), output.lines)
        Files.deleteIfExists(Path.of("out.bin"))
    }

    @Test
    fun `download-media needs a peer and a message id`() {
        val media = FakeDownloadMedia()

        val output = run(listOf("download-media @ada"), fakeUseCases(downloadMedia = media))

        assertEquals(
            listOf(SHELL_WELCOME, "Usage: download-media <peer> <message-id> [target]"),
            output.lines,
        )
        assertEquals(emptyList(), media.downloads)
    }

    @Test
    fun `pin pins the message in the current chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "pin 7"), fakeUseCases(writer = writer))

        assertEquals(listOf(MessageIdCall("@ada", 7)), writer.pins)
        assertTrue(output.text.contains("Pinned message 7."), output.text)
    }

    @Test
    fun `unpin unpins the message in the current chat`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "unpin 7"), fakeUseCases(writer = writer))

        assertEquals(listOf(MessageIdCall("@ada", 7)), writer.unpins)
    }

    @Test
    fun `unpin all unpins every message in the current chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "unpin all"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.unpins)
        assertEquals(listOf("@ada"), writer.unpinnedAll)
        assertTrue(output.text.contains("Unpinned every message in Ada."), output.text)
    }

    @Test
    fun `unpin all needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("unpin all"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.unpinnedAll)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `unpin with a non-numeric id reports the usage`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "unpin abc"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.unpins)
        assertEquals(emptyList(), writer.unpinnedAll)
        assertTrue(output.text.contains("Usage: unpin <id> | unpin all"), output.text)
    }

    @Test
    fun `pinned shows the current chat's pinned message`() {
        val writer = FakeMessageWriter(pinned = message(7, "important"))

        val output = run(listOf("open @ada", "pinned"), fakeUseCases(writer = writer))

        assertEquals(listOf("@ada"), writer.pinnedRequests)
        assertTrue(output.text.contains("important"), output.text)
    }

    @Test
    fun `pinned says when the current chat has none`() {
        val writer = FakeMessageWriter(pinned = null)

        val output = run(listOf("open @ada", "pinned"), fakeUseCases(writer = writer))

        assertTrue(output.text.contains("No pinned message in Ada."), output.text)
    }

    @Test
    fun `pinned needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("pinned"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.pinnedRequests)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `chat-action defaults to typing in the current chat`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "chat-action"), fakeUseCases(writer = writer))

        assertEquals(listOf(ChatActionCall("@ada", ChatActivity.TYPING)), writer.chatActions)
    }

    @Test
    fun `chat-action sends the named status to the current chat`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "chat-action upload-photo"), fakeUseCases(writer = writer))

        assertEquals(listOf(ChatActionCall("@ada", ChatActivity.UPLOAD_PHOTO)), writer.chatActions)
    }

    @Test
    fun `chat-action needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("chat-action"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.chatActions)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `chat-action reports an unknown status`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "chat-action nope"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.chatActions)
        assertTrue(output.text.contains("unknown chat action 'nope'"), output.text)
    }

    @Test
    fun `blocked lists the blocked accounts`() {
        val contacts = FakeContacts(
            blockedContacts = listOf(
                BlockedContact(9, "Mallory", "mal", Instant.parse("2026-01-02T00:00:00Z")),
            ),
        )

        val output = run(listOf("blocked"), fakeUseCases(contacts = contacts))

        assertEquals(listOf(50), contacts.blockedLimits)
        assertTrue(output.text.contains("Mallory"), output.text)
    }

    @Test
    fun `blocked --after passes the cursor to the use case`() {
        val contacts = FakeContacts()

        run(listOf("blocked --after 10"), fakeUseCases(contacts = contacts))

        assertEquals(listOf<String?>("10"), contacts.blockedCursors)
    }

    @Test
    fun `blocked --all passes all to the use case`() {
        val contacts = FakeContacts()

        run(listOf("blocked --all"), fakeUseCases(contacts = contacts))

        assertEquals(listOf(true), contacts.blockedAlls)
    }

    @Test
    fun `blocked prints the next cursor on a full page`() {
        val blocked = (1..50).map { index ->
            BlockedContact(index.toLong(), "User $index", null, Instant.parse("2026-01-02T00:00:00Z"))
        }
        val contacts = FakeContacts(blockedContacts = blocked)

        val output = run(listOf("blocked"), fakeUseCases(contacts = contacts))

        assertTrue(output.text.contains("# next: --after 50"), output.text)
    }

    @Test
    fun `blocked omits the next cursor on a short page`() {
        val contacts = FakeContacts(
            blockedContacts = listOf(
                BlockedContact(9, "Mallory", "mal", Instant.parse("2026-01-02T00:00:00Z")),
            ),
        )

        val output = run(listOf("blocked"), fakeUseCases(contacts = contacts))

        assertFalse(output.text.contains("# next:"), output.text)
    }

    @Test
    fun `blocked rejects a malformed --after`() {
        val contacts = FakeContacts()

        val output = run(listOf("blocked --after abc"), fakeUseCases(contacts = contacts))

        assertTrue(output.text.contains("malformed cursor"), output.text)
        assertEquals(emptyList(), contacts.blockedCursors)
    }

    @Test
    fun `sessions lists the active sessions`() {
        val sessions = FakeSessions(listOf(session(11)))

        val output = run(listOf("sessions"), fakeUseCases(sessions = sessions))

        assertTrue(output.text.contains("11"), output.text)
        assertTrue(output.text.contains("Desktop"), output.text)
    }

    @Test
    fun `sessions terminate takes the exact hash`() {
        val sessions = FakeSessions()

        val output = run(listOf("sessions terminate 22"), fakeUseCases(sessions = sessions))

        assertEquals(listOf(22L), sessions.terminated)
        assertTrue(output.text.contains("Terminated session 22."), output.text)
    }

    @Test
    fun `sessions terminate requires a numeric hash`() {
        val sessions = FakeSessions()

        val output = run(listOf("sessions terminate abc"), fakeUseCases(sessions = sessions))

        assertEquals(emptyList(), sessions.terminated)
        assertTrue(output.text.contains("Usage: sessions terminate <hash>"), output.text)
    }

    @Test
    fun `sessions reports an unknown subcommand`() {
        val sessions = FakeSessions()

        val output = run(listOf("sessions frobnicate"), fakeUseCases(sessions = sessions))

        assertTrue(output.text.contains("Usage: sessions [terminate <hash>]"), output.text)
    }

    @Test
    fun `help commands lists the CLI commands and marks CLI-only ones`() {
        val output = run(listOf("help commands"))

        assertTrue(output.text.contains("CLI commands"), output.text)
        // The list is the CLI's, so a CLI-only command is named and marked as such.
        assertTrue(output.text.contains("send-file"), output.text)
        assertTrue(output.text.contains("permissions"), output.text)
        // And it must never claim a non-verb works in the shell.
        assertTrue(output.text.contains("cli"), output.text)
    }

    @Test
    fun `pin needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("pin 7"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.pins)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `pin with a non-numeric id reports the usage`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "pin abc"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.pins)
        assertTrue(output.text.contains("Usage: pin <id>"), output.text)
    }

    @Test
    fun `react decodes the emoji into the reaction`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "react 7 U+1F44D"), fakeUseCases(writer = writer))

        assertEquals(listOf(ReactCall("@ada", 7, "👍")), writer.reactions)
    }

    @Test
    fun `react needs an open chat and an emoji`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("react 7 👍"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.reactions)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `react with only an id reports the usage`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "react 7"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.reactions)
        assertTrue(output.text.contains("Usage: react <id> <emoji>"), output.text)
    }

    @Test
    fun `unreact removes the reaction`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "unreact 7"), fakeUseCases(writer = writer))

        assertEquals(listOf(MessageIdCall("@ada", 7)), writer.removals)
    }

    @Test
    fun `delete removes the message from the transcript`() {
        val history = FakeReadHistory(listOf(message(7, "old")))
        val writer = FakeMessageWriter()
        val output = RecordingOutput()

        // Read first so the deleted message is in the transcript the shell would show again.
        Shell(
            ScriptedLineSource(listOf("open @ada", "read", "delete 7", "read")),
            output,
            fakeUseCases(history = history, writer = writer),
        ).run()

        assertEquals(listOf(DeleteCall("@ada", listOf(7))), writer.deletes)
        assertTrue(output.text.contains("Deleted 1 message(s)."), output.text)
    }

    @Test
    fun `delete needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("delete 7"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.deletes)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `delete with a non-numeric id reports the usage`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "delete x"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.deletes)
        assertTrue(output.text.contains("Usage: delete <id>"), output.text)
    }

    @Test
    fun `edit refreshes the stale transcript copy`() {
        val history = FakeReadHistory(listOf(message(7, "old")))
        val writer = FakeMessageWriter(edited = message(7, "new"))
        val output = RecordingOutput()

        Shell(
            ScriptedLineSource(listOf("open @ada", "read", "edit 7 new", "read")),
            output,
            fakeUseCases(history = history, writer = writer),
        ).run()

        assertEquals(listOf(EditCall("@ada", 7, "new")), writer.edits)
        // The last `read` must show the edited text, never the stale "old" copy.
        assertTrue(output.text.contains("new"), output.text)
    }

    @Test
    fun `edit needs an open chat and text`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("open @ada", "edit 7"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.edits)
        assertTrue(output.text.contains("Usage: edit <id> <text...>"), output.text)
    }

    @Test
    fun `forward sends a message of the current chat to another peer`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "forward 7 @bob"), fakeUseCases(writer = writer))

        assertEquals(listOf(ForwardCall("@ada", listOf(7), "@bob")), writer.forwards)
    }

    @Test
    fun `forward needs an open chat and a destination`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("forward 7 @bob"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.forwards)
        assertTrue(output.text.contains("No chat open"), output.text)

        val missingTo = run(listOf("open @ada", "forward 7"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.forwards)
        assertTrue(missingTo.text.contains("Usage: forward <id> <to>"), missingTo.text)
    }

    @Test
    fun `mark-read marks the current chat read`() {
        val writer = FakeMessageWriter()

        run(listOf("open @ada", "mark-read"), fakeUseCases(writer = writer))

        assertEquals(listOf("@ada"), writer.markedRead)
    }

    @Test
    fun `mark-read needs an open chat`() {
        val writer = FakeMessageWriter()

        val output = run(listOf("mark-read"), fakeUseCases(writer = writer))

        assertEquals(emptyList(), writer.markedRead)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `invite adds a user to the current chat`() {
        val members = FakeChatMembers()

        run(listOf("open @ada", "invite @bob"), fakeUseCases(members = members))

        assertEquals(listOf("@ada" to "@bob"), members.invites)
    }

    @Test
    fun `kick removes a user from the current chat`() {
        val members = FakeChatMembers()

        run(listOf("open @ada", "kick @bob"), fakeUseCases(members = members))

        assertEquals(listOf("@ada" to "@bob"), members.kicks)
    }

    @Test
    fun `invite and kick need an open chat`() {
        val members = FakeChatMembers()

        val output = run(listOf("invite @bob", "kick @bob"), fakeUseCases(members = members))

        assertEquals(emptyList(), members.invites)
        assertEquals(emptyList(), members.kicks)
        assertTrue(output.text.contains("No chat open"), output.text)
    }

    @Test
    fun `invite and kick without a user report the usage`() {
        val members = FakeChatMembers()

        val output = run(listOf("open @ada", "invite", "kick"), fakeUseCases(members = members))

        assertEquals(emptyList(), members.invites)
        assertEquals(emptyList(), members.kicks)
        assertTrue(output.text.contains("Usage: invite <user>"), output.text)
        assertTrue(output.text.contains("Usage: kick <user>"), output.text)
    }

    @Test
    fun `send-media-url sends into the current chat`() {
        val media = FakeSendMedia()

        run(listOf("open @ada", "send-media-url https://example.test/cat.png"), fakeUseCases(sendMedia = media))

        assertEquals(
            listOf(SendUrlCall("@ada", "https://example.test/cat.png", "", false, null, false)),
            media.urlSends,
        )
    }

    @Test
    fun `send-media-url needs an open chat and a url`() {
        val media = FakeSendMedia()

        val output = run(listOf("send-media-url https://example.test/cat.png"), fakeUseCases(sendMedia = media))

        assertEquals(emptyList(), media.urlSends)
        assertTrue(output.text.contains("No chat open"), output.text)

        val missingUrl = run(listOf("open @ada", "send-media-url"), fakeUseCases(sendMedia = media))

        assertEquals(emptyList(), media.urlSends)
        assertTrue(missingUrl.text.contains("Usage: send-media-url <url>"), missingUrl.text)
    }

    @Test
    fun `copy-media copies into the current chat`() {
        val media = FakeSendMedia()

        run(listOf("open @ada", "copy-media 7"), fakeUseCases(sendMedia = media))

        assertEquals(listOf(CopyMediaCall("@ada", 7, "", null, false)), media.copies)
    }

    @Test
    fun `copy-media needs an open chat and a numeric id`() {
        val media = FakeSendMedia()

        val output = run(listOf("open @ada", "copy-media x"), fakeUseCases(sendMedia = media))

        assertEquals(emptyList(), media.copies)
        assertTrue(output.text.contains("Usage: copy-media <id>"), output.text)

        val noChat = run(listOf("copy-media 7"), fakeUseCases(sendMedia = media))

        assertTrue(noChat.text.contains("No chat open"), noChat.text)
    }

    private fun session(hash: Long) = Session(
        hash = hash,
        deviceModel = "Desktop",
        platform = "Windows",
        appVersion = "0.4.0",
        ip = "203.0.113.7",
        country = "AT",
        createdAt = Instant.parse("2026-01-01T12:30:00Z"),
        current = true,
    )

    private fun message(id: Int, text: String, outgoing: Boolean = false) = Message(
        id = id,
        senderName = if (outgoing) "You" else "Ada",
        text = text,
        sentAt = Instant.parse("2026-01-01T00:00:00Z"),
        outgoing = outgoing,
    )

    private fun fileMessage(id: Int, name: String) = Message(
        id = id,
        senderName = "Ada",
        text = "",
        sentAt = Instant.parse("2026-01-01T00:00:00Z"),
        outgoing = false,
        media = MediaInfo(kind = "video", sizeBytes = 1024, name = name),
    )

    private fun row(id: Int, text: String, outgoing: Boolean = false) =
        "$id\t2026-01-01T00:00:00Z\t${if (outgoing) "You" else "Ada"}\t\t\t\t\t\t$text"

    private companion object {
        const val MESSAGE_ROW_HEADER = "id\ttime\tfrom\tvia\treply\tquote\tmedia\taction\ttext"
    }
}
