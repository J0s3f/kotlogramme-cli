package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.kotlogramme.TelegramException
import org.kotlogramme.cli.KotlogrammeCommand
import org.kotlogramme.cli.adapter.format.UploadProgressBar
import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.api.LoginStep
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.port.spi.UploadProgress
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.application.port.spi.UploadProgressSlot
import org.kotlogramme.cli.domain.Account
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport
import org.kotlogramme.cli.domain.Folder
import org.kotlogramme.cli.domain.IncomingUpdate
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.InlineResult
import org.kotlogramme.cli.domain.MediaFileKind
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Participant
import org.kotlogramme.cli.domain.StickerPack
import org.kotlogramme.cli.domain.StickerSet
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/** Captures everything a command writes so a test can assert on it. */
internal class RecordingOutput : Output {
    private val recorded = mutableListOf<String>()

    val lines: List<String> get() = recorded

    val text: String get() = recorded.joinToString("\n")

    override fun line(text: String) {
        recorded += text
    }

    override fun table(headers: List<String>, rows: List<List<String>>, title: String?) {
        title?.let { recorded += it }
        recorded += headers.joinToString("\t")
        rows.forEach { recorded += it.joinToString("\t") }
    }
}

/**
 * A reporter that watches an upload and remembers its total, but paints nothing.
 *
 * A test that is about which total the command declares, rather than about how a bar draws it, needs
 * a watcher without the drawing: this is the reporter to pass, and [totals] says what it was told.
 */
internal class SilentWatcher : UploadProgressReporter {
    val totals = mutableListOf<Long>()

    private var slot: UploadProgressSlot? = null

    override fun begin(totalBytes: Long): UploadProgressSlot {
        totals += totalBytes
        return TestProgressSlot().also { slot = it }
    }

    /** The slot the last upload was given, for a test that needs the reading the bar would have seen. */
    fun current(): UploadProgress? = slot?.current()
}

/** A slot with nothing behind it, which is all a total-declaring test needs. */
private class TestProgressSlot : UploadProgressSlot {
    override val isWatched: Boolean = true

    private var counter: (() -> UploadProgress?)? = null

    override fun follow(counter: () -> UploadProgress?) {
        this.counter = counter
    }

    override fun current(): UploadProgress? = counter?.invoke()

    override fun close() = Unit
}

/**
 * Runs [body] against the slot and closes it afterwards, which is the lifetime a real upload has:
 * opened before the bytes move, closed once the send ends.
 */
internal inline fun <T> UploadProgressSlot.closing(body: UploadProgressSlot.() -> T): T =
    try {
        body()
    } finally {
        close()
    }

/** An in-memory [ConfigStore] that records the last value saved. */
internal class FakeConfigStore(private var config: AppConfig) : ConfigStore {
    override fun load(): AppConfig = config

    override fun save(config: AppConfig) {
        this.config = config
    }
}

/** An [Authenticate] that returns canned steps and records the calls it receives. */
internal class FakeAuthenticate(
    private val status: AccountStatus = AccountStatus.Anonymous,
    private val startStep: LoginStep = LoginStep.CodeRequired(testPhone, hint = null),
    private val codeStep: LoginStep = LoginStep.SignedIn(testAccount),
    private val passwordStep: LoginStep = LoginStep.SignedIn(testAccount),
) : Authenticate {
    val startedLogins = mutableListOf<String>()
    val submittedCodes = mutableListOf<Pair<String, String>>()
    val submittedPasswords = mutableListOf<String>()
    val botTokens = mutableListOf<String>()
    var logoutCount = 0

    override fun status(): AccountStatus = status

    override fun startLogin(phoneNumber: String): LoginStep {
        startedLogins += phoneNumber
        return startStep
    }

    override fun submitCode(phoneNumber: String, code: String): LoginStep {
        submittedCodes += phoneNumber to code
        return codeStep
    }

    override fun submitPassword(password: String): LoginStep {
        submittedPasswords += password
        return passwordStep
    }

    override fun loginWithBotToken(botToken: String): Account {
        botTokens += botToken
        return testAccount
    }

    override fun logout() {
        logoutCount++
    }
}

/** A [ListDialogs] returning canned chats and recording every requested limit. */
internal class FakeListDialogs(private val dialogs: List<Chat> = emptyList()) : ListDialogs {
    val limits = mutableListOf<Int>()

    override fun list(limit: Int): List<Chat> {
        limits += limit
        return dialogs
    }
}

/** A history page request: the reference, the limit and the paging cursor. */
internal data class HistoryCall(val reference: String, val limit: Int, val beforeMessageId: Int?)

/** A [ReadHistory] returning canned messages and recording every request. */
internal class FakeReadHistory(private val messages: List<Message> = emptyList()) : ReadHistory {
    val calls = mutableListOf<HistoryCall>()

    override fun read(reference: String, limit: Int, beforeMessageId: Int?): List<Message> {
        calls += HistoryCall(reference, limit, beforeMessageId)
        return messages
    }
}

/** A search request: the optional chat reference, the query and the requested limit. */
internal data class SearchCall(val reference: String?, val query: String, val limit: Int)

/** A file listing request: the chat reference, the media kind and the requested limit. */
internal data class FileSearchCall(val reference: String, val kind: MediaFileKind, val limit: Int)

/** A [SearchMessages] returning canned matches and recording every query. */
internal class FakeSearchMessages(
    private val results: List<Message> = emptyList(),
    private val totalResults: Int = 0,
    private val fileResults: List<Message> = emptyList(),
    private val fileTotalResults: Int = 0,
) : SearchMessages {
    val searches = mutableListOf<SearchCall>()
    val totals = mutableListOf<SearchCall>()
    val fileSearches = mutableListOf<FileSearchCall>()
    val fileTotals = mutableListOf<Pair<String, MediaFileKind>>()

    override fun search(reference: String?, query: String, limit: Int): List<Message> {
        searches += SearchCall(reference, query, limit)
        return results
    }

    override fun total(reference: String?, query: String): Int {
        totals += SearchCall(reference, query, 0)
        return totalResults
    }

    override fun files(reference: String, kind: MediaFileKind, limit: Int): List<Message> {
        fileSearches += FileSearchCall(reference, kind, limit)
        return fileResults
    }

    override fun fileTotal(reference: String, kind: MediaFileKind): Int {
        fileTotals += reference to kind
        return fileTotalResults
    }
}

/** A [Contacts] returning canned contacts and recording every request. */
internal class FakeContacts(
    private val contacts: List<Contact> = emptyList(),
    private val searchResults: List<Contact> = emptyList(),
    private val blockedContacts: List<BlockedContact> = emptyList(),
    private val importedContacts: List<Contact> = emptyList(),
    private val retryCount: Int = 0,
) : Contacts {
    val limits = mutableListOf<Int>()
    val blockedLimits = mutableListOf<Int>()
    val searches = mutableListOf<Pair<String, Int>>()
    val blocked = mutableListOf<String>()
    val unblocked = mutableListOf<String>()
    val imports = mutableListOf<List<ContactToImport>>()
    val deleted = mutableListOf<String>()

    override fun list(limit: Int): List<Contact> {
        limits += limit
        return contacts
    }

    override fun search(query: String, limit: Int): List<Contact> {
        searches += query to limit
        return searchResults
    }

    override fun block(reference: String) {
        blocked += reference
    }

    override fun unblock(reference: String) {
        unblocked += reference
    }

    override fun blocked(limit: Int): List<BlockedContact> {
        blockedLimits += limit
        return blockedContacts
    }

    override fun import(contacts: List<ContactToImport>): ContactImportSummary {
        imports += contacts
        return ContactImportSummary(imported = importedContacts, retryCount = retryCount)
    }

    override fun delete(reference: String) {
        deleted += reference
    }
}

/** A members request: the chat reference and the requested limit. */
internal data class MemberCall(val reference: String, val limit: Int)

/** A [ChatMembers] returning canned members and recording every request. */
internal class FakeChatMembers(private val members: List<Participant> = emptyList()) : ChatMembers {
    val lists = mutableListOf<MemberCall>()
    val invites = mutableListOf<Pair<String, String>>()
    val kicks = mutableListOf<Pair<String, String>>()

    override fun list(reference: String, limit: Int): List<Participant> {
        lists += MemberCall(reference, limit)
        return members
    }

    override fun invite(reference: String, userReference: String) {
        invites += reference to userReference
    }

    override fun kick(reference: String, userReference: String) {
        kicks += reference to userReference
    }
}

/** A [ListFolders] returning canned folders. */
internal class FakeListFolders(private val folders: List<Folder> = emptyList()) : ListFolders {
    override fun list(): List<Folder> = folders
}

/** An admin-rights request: the chat and the member reference. */
internal data class AdminRefs(val reference: String, val userReference: String)

/** A promotion request with the rights it granted. */
internal data class PromoteCall(val reference: String, val userReference: String, val rights: ChatRights)

/** A restriction request with the restrictions it applied. */
internal data class RestrictCall(
    val reference: String,
    val userReference: String,
    val restrictions: ChatRestrictions,
)

/** An [AdminRights] returning canned rights and recording every request. */
internal class FakeAdminRights(
    private val rights: ChatRights = ChatRights.NONE,
    private val rejection: IllegalArgumentException? = null,
) : AdminRights {
    val permissionCalls = mutableListOf<AdminRefs>()
    val promotions = mutableListOf<PromoteCall>()
    val restrictions = mutableListOf<RestrictCall>()

    /** Simulates the use case rejecting the input before it reaches the gateway. */
    private fun reject() {
        rejection?.let { throw it }
    }

    override fun permissions(reference: String, userReference: String): ChatRights {
        reject()
        permissionCalls += AdminRefs(reference, userReference)
        return rights
    }

    override fun promote(reference: String, userReference: String, rights: ChatRights) {
        reject()
        promotions += PromoteCall(reference, userReference, rights)
    }

    override fun restrict(reference: String, userReference: String, restrictions: ChatRestrictions) {
        reject()
        this.restrictions += RestrictCall(reference, userReference, restrictions)
    }
}

/** A [Listen] that replays canned updates and stops as soon as the predicate asks it to. */
internal class FakeListen(private val updates: List<IncomingUpdate> = emptyList()) : Listen {
    override fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int {
        var handled = 0
        for (update in updates) {
            if (stop()) break
            onUpdate(update)
            handled++
        }
        return handled
    }
}

/** A [Listen] that never delivers an update, for a test about stop latency. */
internal class QuietListen(private val waitMillis: Long = 0L) : Listen {
    override fun run(stop: () -> Boolean, onUpdate: (IncomingUpdate) -> Unit): Int {
        if (waitMillis > 0) Thread.sleep(waitMillis)
        return 0
    }
}

/** An inline query: the bot, the query and the chat context it was asked in. */
internal data class InlineQueryCall(val bot: String, val query: String, val reference: String?)

/** An inline send: the destination, the query id carried from the answer and the result id. */
internal data class InlineSendCall(val reference: String, val queryId: Long, val resultId: String)

/** An [InlineBots] returning a canned answer and recording every query and send. */
internal class FakeInlineBots(
    private val answer: InlineQuery = InlineQuery(queryId = 1L, results = emptyList()),
    private val sent: Message? = testMessage,
    private val rejection: IllegalArgumentException? = null,
) : InlineBots {
    val queries = mutableListOf<InlineQueryCall>()
    val sends = mutableListOf<InlineSendCall>()

    /** Simulates the use case rejecting the input before it reaches the gateway. */
    private fun reject() {
        rejection?.let { throw it }
    }

    override fun query(bot: String, query: String, reference: String?): InlineQuery {
        reject()
        queries += InlineQueryCall(bot, query, reference)
        return answer
    }

    override fun send(reference: String, queryId: Long, resultId: String): Message? {
        reject()
        sends += InlineSendCall(reference, queryId, resultId)
        return sent
    }
}

/** A result the inline tests can list or send. */
internal fun inlineResult(id: String, title: String, text: String? = null) =
    InlineResult(id = id, type = "article", title = title, description = null, text = text)

/** A sticker send request: the chat, the set, the index and the options the command parsed. */
internal data class StickerSendCall(
    val reference: String,
    val setReference: String,
    val index: Int,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

/** A [Stickers] returning canned sets and recording every request. */
internal class FakeStickers(
    private val sets: List<StickerSet> = emptyList(),
    private val setAnswer: StickerSet? = null,
    private val sent: Message = testMessage,
) : Stickers {
    val setRequests = mutableListOf<String>()
    val sends = mutableListOf<StickerSendCall>()

    override fun sets(): List<StickerSet> = sets

    override fun set(reference: String): StickerSet {
        setRequests += reference
        return setAnswer ?: error("no sticker set configured for '$reference'")
    }

    override fun send(
        chatReference: String,
        setReference: String,
        index: Int,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        sends += StickerSendCall(chatReference, setReference, index, replyToMessageId, silent)
        return sent
    }
}

/** A sticker set the shell tests can list and open, numbered by [documents]. */
internal fun testStickerSet(
    shortName: String = "cats",
    title: String = "Cats",
    documents: List<Long> = listOf(11L, 22L),
) = StickerSet(
    id = 42,
    accessHash = 99,
    title = title,
    shortName = shortName,
    count = documents.size,
    packs = listOf(StickerPack(emoticon = "🐱", documentIds = documents)),
    documents = documents,
)

/** A send request with every field the command parsed. */
internal data class SendCall(val reference: String, val text: String, val replyToMessageId: Int?, val silent: Boolean)

/** An edit request. */
internal data class EditCall(val reference: String, val messageId: Int, val text: String)

/** A delete request. */
internal data class DeleteCall(val reference: String, val messageIds: List<Int>)

/** A forward request. */
internal data class ForwardCall(val fromReference: String, val messageIds: List<Int>, val toReference: String)

/** Any call that only names one message in one chat. */
internal data class MessageIdCall(val reference: String, val messageId: Int)

/** A reaction request. */
internal data class ReactCall(val reference: String, val messageId: Int, val emoji: String)

/** A [MessageWriter] that records every mutation and returns canned results. */
internal class FakeMessageWriter(
    private val sent: Message = testMessage,
    private val edited: Message = testMessage,
    private val deletedCount: Int = 1,
    private val forwarded: List<Message> = listOf(testMessage),
    private val rejection: IllegalArgumentException? = null,
) : MessageWriter {
    val sends = mutableListOf<SendCall>()
    val edits = mutableListOf<EditCall>()
    val deletes = mutableListOf<DeleteCall>()
    val forwards = mutableListOf<ForwardCall>()
    val pins = mutableListOf<MessageIdCall>()
    val unpins = mutableListOf<MessageIdCall>()
    val reactions = mutableListOf<ReactCall>()
    val removals = mutableListOf<MessageIdCall>()
    val markedRead = mutableListOf<String>()

    /** Simulates the use case rejecting the input before it reaches the gateway. */
    private fun reject() {
        rejection?.let { throw it }
    }

    override fun sendText(reference: String, text: String, replyToMessageId: Int?, silent: Boolean): Message {
        reject()
        sends += SendCall(reference, text, replyToMessageId, silent)
        return sent
    }

    override fun edit(reference: String, messageId: Int, text: String): Message {
        reject()
        edits += EditCall(reference, messageId, text)
        return edited
    }

    override fun delete(reference: String, messageIds: List<Int>): Int {
        reject()
        deletes += DeleteCall(reference, messageIds)
        return deletedCount
    }

    override fun forward(fromReference: String, messageIds: List<Int>, toReference: String): List<Message> {
        reject()
        forwards += ForwardCall(fromReference, messageIds, toReference)
        return forwarded
    }

    override fun pin(reference: String, messageId: Int) {
        reject()
        pins += MessageIdCall(reference, messageId)
    }

    override fun unpin(reference: String, messageId: Int) {
        reject()
        unpins += MessageIdCall(reference, messageId)
    }

    override fun react(reference: String, messageId: Int, emoji: String) {
        reject()
        reactions += ReactCall(reference, messageId, emoji)
    }

    override fun removeReaction(reference: String, messageId: Int) {
        reject()
        removals += MessageIdCall(reference, messageId)
    }

    override fun markRead(reference: String) {
        reject()
        markedRead += reference
    }
}

/** A file send request with every field the command parsed. */
internal data class SendFileCall(
    val reference: String,
    val path: Path,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

/** A video send request with the metadata the command parsed. */
internal data class SendVideoCall(
    val reference: String,
    val path: Path,
    val caption: String,
    val durationSeconds: Double?,
    val width: Int?,
    val height: Int?,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

/** A stream send request, carrying the bytes the stream held and the length it declared. */
internal data class SendStreamCall(
    val reference: String,
    val name: String,
    val content: String,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
    val size: Long = content.toByteArray().size.toLong(),
    /**
     * The exact bytes the stream carried, which a [content] comparison cannot show: the point of
     * reading a pipe as bytes is that a byte no decoder likes survives it.
     */
    val bytes: ByteArray = content.toByteArray(),
) {
    override fun equals(other: Any?): Boolean =
        other is SendStreamCall &&
            reference == other.reference &&
            name == other.name &&
            content == other.content &&
            caption == other.caption &&
            asPhoto == other.asPhoto &&
            replyToMessageId == other.replyToMessageId &&
            silent == other.silent &&
            size == other.size

    override fun hashCode(): Int =
        listOf(reference, name, caption, asPhoto, replyToMessageId, silent, size).hashCode()
}

/** A URL send request. */
internal data class SendUrlCall(
    val reference: String,
    val url: String,
    val caption: String,
    val asPhoto: Boolean,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

/** A copy request naming the source message. */
internal data class CopyMediaCall(
    val reference: String,
    val fromMessageId: Int,
    val caption: String,
    val replyToMessageId: Int?,
    val silent: Boolean,
)

/** A [SendMedia] that records every send and returns a canned result. */
internal class FakeSendMedia(
    private val sent: Message = testMessage,
    private val rejection: IllegalArgumentException? = null,
) : SendMedia {
    val fileSends = mutableListOf<SendFileCall>()
    val videoSends = mutableListOf<SendVideoCall>()
    val streamSends = mutableListOf<SendStreamCall>()
    val urlSends = mutableListOf<SendUrlCall>()
    val copies = mutableListOf<CopyMediaCall>()

    /** Every reporter a send was handed, so a test can tell a silent run from a watched one. */
    val progressReports = mutableListOf<UploadProgressReporter>()

    /** Simulates the use case rejecting the input before it reaches the gateway. */
    private fun reject() {
        rejection?.let { throw it }
    }

    override fun sendFile(
        reference: String,
        path: Path,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressReporter,
    ): Message {
        reject()
        fileSends += SendFileCall(reference, path, caption, asPhoto, replyToMessageId, silent)
        progressReports += progress
        // The service owns the slot's lifetime and the gateway fills it; a fake standing in for both
        // opens one with the file's own length and reports a complete transfer, which is what a real
        // one does and what a bar in a test has to be able to read.
        val size = Files.size(path)
        progress.begin(size).closing {
            follow { UploadProgress(bytesSent = size, totalBytes = size, elapsedMillis = 1_000) }
        }
        return sent
    }

    override fun sendVideo(
        reference: String,
        path: Path,
        caption: String,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        replyToMessageId: Int?,
        silent: Boolean,
        progress: UploadProgressReporter,
    ): Message {
        reject()
        videoSends += SendVideoCall(reference, path, caption, durationSeconds, width, height, replyToMessageId, silent)
        progressReports += progress
        val size = Files.size(path)
        progress.begin(size).closing {
            follow { UploadProgress(bytesSent = size, totalBytes = size, elapsedMillis = 1_000) }
        }
        return sent
    }

    override fun sendStream(
        reference: String,
        name: String,
        data: InputStream,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
        size: Long,
        progress: UploadProgressReporter,
    ): Message {
        val bytes = data.readBytes()
        reject()
        streamSends += SendStreamCall(
            reference,
            name,
            bytes.decodeToString(),
            caption,
            asPhoto,
            replyToMessageId,
            silent,
            size,
            bytes,
        )
        progressReports += progress
        progress.begin(size).closing {
            follow { UploadProgress(bytesSent = size, totalBytes = size, elapsedMillis = 1_000) }
        }
        return sent
    }

    override fun sendUrl(
        reference: String,
        url: String,
        caption: String,
        asPhoto: Boolean,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        reject()
        urlSends += SendUrlCall(reference, url, caption, asPhoto, replyToMessageId, silent)
        return sent
    }

    override fun copyMedia(
        reference: String,
        fromMessageId: Int,
        caption: String,
        replyToMessageId: Int?,
        silent: Boolean,
    ): Message {
        reject()
        copies += CopyMediaCall(reference, fromMessageId, caption, replyToMessageId, silent)
        return sent
    }
}

/** A download request: the peer, the message id and the target written. */
internal data class DownloadMediaCall(val reference: String, val messageId: Int, val target: Path)

/** A [DownloadMedia] that writes canned bytes to the target and records every call. */
internal class FakeDownloadMedia(
    private val mediaName: String? = "cat.png",
    private val rejection: IllegalArgumentException? = null,
    private val telegramRejection: TelegramException? = null,
) : DownloadMedia {
    val downloads = mutableListOf<DownloadMediaCall>()
    val fileNameCalls = mutableListOf<Pair<String, Int>>()

    /** Simulates the use case rejecting the input before it reaches the gateway. */
    private fun reject() {
        rejection?.let { throw it }
        telegramRejection?.let { throw it }
    }

    override fun download(reference: String, messageId: Int, target: Path): Path {
        reject()
        downloads += DownloadMediaCall(reference, messageId, target)
        Files.write(target, DOWNLOAD_BYTES)
        return target
    }

    override fun fileName(reference: String, messageId: Int): String? {
        reject()
        fileNameCalls += reference to messageId
        return mediaName
    }

    private companion object {
        /** The bytes a download writes, so the command can report the size the file holds. */
        val DOWNLOAD_BYTES = ByteArray(64) { it.toByte() }
    }
}

/**
 * Drives the whole command tree the way `main` does, against injected fakes.
 *
 * [stdin] is the text a command that reads a message body from `-` sees. A command that reads raw
 * bytes takes its own stream instead, through [sendFileCommand], because text is the wrong shape for
 * bytes and pretending otherwise is the bug [CliFixture.sendFileWithBytes] exists to catch.
 */
internal class CliFixture(
    val output: RecordingOutput,
    val authenticate: FakeAuthenticate,
    private val root: KotlogrammeCommand,
) {
    fun run(vararg args: String, stdin: String = "") = root.test(args.toList(), stdin)
}

/**
 * A `send-file` command whose `-` path reads [bytes] rather than the process's own stdin.
 *
 * Production reads `System.in` and nothing else; this only lets a test pipe bytes that no decoder
 * would survive, which is exactly what a real pipe can carry.
 */
internal fun sendFileWithBytes(bytes: ByteArray): SendFileCommand =
    SendFileCommand(ByteArrayInputStream(bytes))

/** [sendFileWithBytes] for a payload written as bytes in the test. */
internal fun sendFileWithText(text: String): SendFileCommand = sendFileWithBytes(text.toByteArray())

internal fun cliFixture(
    config: AppConfig = AppConfig(
        credentials = ApiCredentials(apiId = 1, apiHash = "configured-hash"),
        sessionPath = Paths.get("session.sqlite"),
    ),
    configStore: ConfigStore = FakeConfigStore(config),
    output: RecordingOutput = RecordingOutput(),
    authenticate: FakeAuthenticate = FakeAuthenticate(),
    listDialogs: ListDialogs = FakeListDialogs(),
    readHistory: ReadHistory = FakeReadHistory(),
    messageWriter: MessageWriter = FakeMessageWriter(),
    contacts: Contacts = FakeContacts(),
    searchMessages: SearchMessages = FakeSearchMessages(),
    chatMembers: ChatMembers = FakeChatMembers(),
    adminRights: AdminRights = FakeAdminRights(),
    listFolders: ListFolders = FakeListFolders(),
    listen: Listen = FakeListen(),
    inline: InlineBots = FakeInlineBots(),
    sendMedia: SendMedia = FakeSendMedia(),
    downloadMedia: DownloadMedia = FakeDownloadMedia(),
    configDir: Path = Paths.get("config"),
    environment: Map<String, String> = emptyMap(),
    nativeLibraryProbe: NativeLibraryProbe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded(null) },
    sendFileCommand: SendFileCommand = SendFileCommand(),
    interactiveTerminal: Boolean = false,
    progressFactory: () -> UploadProgressReporter = { UploadProgressBar() },
): CliFixture {
    val context = AppContext(
        configDir = configDir,
        configStore = configStore,
        output = output,
        environment = environment,
        authenticateFactory = { authenticate },
        listDialogsFactory = { listDialogs },
        readHistoryFactory = { readHistory },
        messageWriterFactory = { messageWriter },
        contactsFactory = { contacts },
        searchMessagesFactory = { searchMessages },
        chatMembersFactory = { chatMembers },
        adminRightsFactory = { adminRights },
        listFoldersFactory = { listFolders },
        listenFactory = { listen },
        inlineFactory = { inline },
        sendMediaFactory = { sendMedia },
        isInteractiveTerminal = interactiveTerminal,
        progressFactory = progressFactory,
        downloadMediaFactory = { downloadMedia },
    )
    val root = KotlogrammeCommand { _, _, _ -> context }
        .subcommands(
            ConfigCommand(),
            LoginCommand(),
            LogoutCommand(),
            WhoamiCommand(),
            DoctorCommand(nativeLibraryProbe),
            DialogsCommand(),
            HistoryCommand(),
            SendCommand(),
            sendFileCommand,
            SendMediaUrlCommand(),
            CopyMediaCommand(),
            DownloadMediaCommand(),
            ListFilesCommand(),
            EditCommand(),
            DeleteCommand(),
            ForwardCommand(),
            PinCommand(),
            UnpinCommand(),
            ReactCommand(),
            UnreactCommand(),
            MarkReadCommand(),
            ContactsCommand(),
            SearchContactsCommand(),
            BlockCommand(),
            UnblockCommand(),
            BlockedCommand(),
            ImportContactsCommand(),
            DeleteContactCommand(),
            SearchCommand(),
            MembersCommand(),
            KickCommand(),
            PermissionsCommand(),
            PromoteCommand(),
            RestrictCommand(),
            ListenCommand(),
            FoldersCommand(),
            InlineCommand(),
            ShellCommand(),
        )
    return CliFixture(output, authenticate, root)
}

internal val testAccount = Account(
    id = 42,
    firstName = "Ada",
    lastName = "Lovelace",
    username = "ada",
    phoneNumber = "+15550100",
)

internal val testMessage = Message(
    id = 7,
    senderName = "Ada Lovelace",
    text = "hello",
    sentAt = Instant.parse("2026-01-01T12:30:00Z"),
    outgoing = true,
)

internal const val testPhone = "+15550142"
