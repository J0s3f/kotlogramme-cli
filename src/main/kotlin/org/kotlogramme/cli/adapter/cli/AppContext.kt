package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktError
import org.kotlogramme.cli.adapter.config.ConfigPaths
import org.kotlogramme.cli.adapter.config.JsonConfigStore
import org.kotlogramme.cli.adapter.format.ConsoleOutput
import org.kotlogramme.cli.adapter.format.MessageStyler
import org.kotlogramme.cli.adapter.format.UploadProgressBar
import org.kotlogramme.cli.adapter.format.TerminalColumns
import org.kotlogramme.cli.adapter.format.TableWidth
import org.kotlogramme.cli.adapter.format.colorEnabled
import org.kotlogramme.cli.adapter.format.messageStylerFor
import org.kotlogramme.cli.adapter.media.FileMediaProbe
import org.kotlogramme.cli.adapter.system.SystemClock
import org.kotlogramme.cli.adapter.telegram.ChatReferenceResolver
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramAdminOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramAdminRightsGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramChatGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramChatOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramContactGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramContactOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramFolderGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramFolderOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramInlineGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramInlineOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramMediaGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMediaOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageSearchGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageWriteGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageWriteOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramParticipantGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramParticipantOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramPhotoGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramPhotoOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramSearchOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramSessionGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramSessionOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramStickerGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramStickerOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramUpdateLoop
import org.kotlogramme.cli.adapter.telegram.KotlogramUserGateway
import org.kotlogramme.cli.adapter.telegram.UserNameCache
import org.kotlogramme.cli.adapter.telegram.KotlogramUserOperations
import org.kotlogramme.cli.adapter.telegram.TelegramClientFactory
import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.Photos
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.Clock
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.MediaGateway
import org.kotlogramme.cli.application.port.spi.MediaProbe
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.application.port.spi.UploadProgressReporter
import org.kotlogramme.cli.application.service.AdminRightsService
import org.kotlogramme.cli.application.service.AuthenticateService
import org.kotlogramme.cli.application.service.ChatMembersService
import org.kotlogramme.cli.application.service.ContactsService
import org.kotlogramme.cli.application.service.DownloadMediaService
import org.kotlogramme.cli.application.service.InlineService
import org.kotlogramme.cli.application.service.ListDialogsService
import org.kotlogramme.cli.application.service.ListFoldersService
import org.kotlogramme.cli.application.service.ListenService
import org.kotlogramme.cli.application.service.MessageWritingService
import org.kotlogramme.cli.application.service.PhotosService
import org.kotlogramme.cli.application.service.ReadHistoryService
import org.kotlogramme.cli.application.service.SearchMessagesService
import org.kotlogramme.cli.application.service.SendMediaService
import org.kotlogramme.cli.application.service.SessionsService
import org.kotlogramme.cli.application.service.StickerService
import java.io.Writer
import java.nio.file.Path

/**
 * The composition root: every dependency a command needs, built in one place.
 *
 * The config directory, the store and the output are resolved once; the use cases are built on
 * demand because they create a Telegram client, which should only happen for commands that
 * actually talk to Telegram. The factories are constructor parameters so tests can swap in fakes.
 */
class AppContext(
    /** The directory that holds `config.json` and the default session file. */
    val configDir: Path,
    private val configStore: ConfigStore,
    val output: Output,
    private val environment: Map<String, String>,
    /** The entity renderer History and the shell use; plain everywhere else by default. */
    val messageStyler: MessageStyler = MessageStyler.PLAIN,
    /**
     * The format [output] renders in. Kept here so a terminal-backed output can be built later, once
     * the shell has a JLine terminal to write through; see [outputOn].
     */
    private val outputFormat: OutputFormat = OutputFormat.TABLE,
    private val authenticateFactory: (AppConfig) -> Authenticate = ::defaultAuthenticate,
    private val listDialogsFactory: (AppConfig) -> ListDialogs = ::defaultListDialogs,
    private val readHistoryFactory: (AppConfig) -> ReadHistory = ::defaultReadHistory,
    private val messageWriterFactory: (AppConfig) -> MessageWriter = ::defaultMessageWriter,
    private val contactsFactory: (AppConfig) -> Contacts = ::defaultContacts,
    private val searchMessagesFactory: (AppConfig) -> SearchMessages = ::defaultSearchMessages,
    private val chatMembersFactory: (AppConfig) -> ChatMembers = ::defaultChatMembers,
    private val adminRightsFactory: (AppConfig) -> AdminRights = ::defaultAdminRights,
    private val listFoldersFactory: (AppConfig) -> ListFolders = ::defaultListFolders,
    private val listenFactory: (AppConfig) -> Listen = ::defaultListen,
    private val stickersFactory: (AppConfig) -> Stickers = ::defaultStickers,
    private val inlineFactory: (AppConfig) -> InlineBots = ::defaultInline,
    private val sendMediaFactory: (AppConfig) -> SendMedia = ::defaultSendMedia,
    private val downloadMediaFactory: (AppConfig) -> DownloadMedia = ::defaultDownloadMedia,
    private val sessionsFactory: (AppConfig) -> Sessions = ::defaultSessions,
    private val photosFactory: (AppConfig) -> Photos = ::defaultPhotos,
    private val mediaProbeFactory: () -> MediaProbe = ::FileMediaProbe,
    /**
     * Whether the output is a terminal a person is watching, which is what puts an upload's progress
     * bar on by default.
     */
    val isInteractiveTerminal: Boolean = false,
    private val progressFactory: () -> UploadProgressReporter = { UploadProgressBar() },
    private val tableWidth: TableWidth = TableWidth.Detect,
    /** The time every command that expires or compares against now asks for. */
    val clock: Clock = SystemClock(),
) {
    /** The configuration as it is on disk right now. */
    fun config(): AppConfig = configStore.load()

    /** Persists [config], replacing whatever was stored before. */
    fun save(config: AppConfig) {
        configStore.save(config)
    }

    /**
     * An [Output] that renders in the same format but writes through [writer] instead of the process's
     * own stdout.
     *
     * The interactive shell has a JLine terminal, whose writer reaches the console through
     * `WriteConsoleW`; writing message bodies through it keeps non-ASCII and emoji intact at any
     * console code page, where [output]'s `System.out` would have turned them into `?` first. The
     * fallback [output] stays the seam for every run without a terminal.
     */
    fun outputOn(writer: Writer, terminalWidth: () -> Int? = { null }): Output =
        ConsoleOutput(outputFormat, writer) { tableWidth.resolve(terminalWidth) }

    /**
     * The credentials for this run: the ones in the config, falling back to the `TG_API_ID` and
     * `TG_API_HASH` environment variables.
     */
    fun credentials(config: AppConfig = config()): ApiCredentials? =
        config.credentials ?: environmentCredentials()

    /**
     * The authentication use case, or a user-facing error when no credentials are configured.
     *
     * The error is a [CliktError] so the process prints the actionable message without a stack
     * trace.
     */
    fun authenticate(): Authenticate = authenticateFactory(configured())

    /** The dialog-listing use case, with the same missing-credentials error as [authenticate]. */
    fun listDialogs(): ListDialogs = listDialogsFactory(configured())

    /** The history-reading use case, with the same missing-credentials error as [authenticate]. */
    fun readHistory(): ReadHistory = readHistoryFactory(configured())

    /** The message-writing use case, with the same missing-credentials error as [authenticate]. */
    fun messageWriter(): MessageWriter = messageWriterFactory(configured())

    /** The contact-listing use case, with the same missing-credentials error as [authenticate]. */
    fun contacts(): Contacts = contactsFactory(configured())

    /** The message-search use case, with the same missing-credentials error as [authenticate]. */
    fun searchMessages(): SearchMessages = searchMessagesFactory(configured())

    /** The chat-membership use case, with the same missing-credentials error as [authenticate]. */
    fun chatMembers(): ChatMembers = chatMembersFactory(configured())

    /** The admin-rights use case, with the same missing-credentials error as [authenticate]. */
    fun adminRights(): AdminRights = adminRightsFactory(configured())

    /** The dialog-folder use case, with the same missing-credentials error as [authenticate]. */
    fun listFolders(): ListFolders = listFoldersFactory(configured())

    /** The update-following use case, with the same missing-credentials error as [authenticate]. */
    fun listen(): Listen = listenFactory(configured())

    /** The sticker use case, with the same missing-credentials error as [authenticate]. */
    fun stickers(): Stickers = stickersFactory(configured())

    /** The inline-bot use case, with the same missing-credentials error as [authenticate]. */
    fun inline(): InlineBots = inlineFactory(configured())

    /** The media-sending use case, with the same missing-credentials error as [authenticate]. */
    fun sendMedia(): SendMedia = sendMediaFactory(configured())

    /** The media-downloading use case, with the same missing-credentials error as [authenticate]. */
    fun downloadMedia(): DownloadMedia = downloadMediaFactory(configured())

    /** The session-management use case, with the same missing-credentials error as [authenticate]. */
    fun sessions(): Sessions = sessionsFactory(configured())

    /** The photo-listing use case, with the same missing-credentials error as [authenticate]. */
    fun photos(): Photos = photosFactory(configured())

    /** The media probe: what a local file should be sent as, and the video metadata it carries. */
    fun mediaProbe(): MediaProbe = mediaProbeFactory()

    /**
     * Where an upload reports itself, or [UploadProgressReporter.SILENT] when [enabled] is false.
     *
     * Each upload gets its own reporter, because a bar belongs to one transfer: the command that
     * resolved the flags decides whether it is enabled at all.
     */
    fun uploadProgress(enabled: Boolean): UploadProgressReporter =
        if (enabled) progressFactory() else UploadProgressReporter.SILENT

    private fun configured(): AppConfig {
        val config = config()
        val credentials = credentials(config) ?: throw MissingCredentialsError()
        return config.copy(credentials = credentials)
    }

    private fun environmentCredentials(): ApiCredentials? {
        val apiId = environment[API_ID_ENV]?.toIntOrNull() ?: return null
        val apiHash = environment[API_HASH_ENV]?.takeIf(String::isNotBlank) ?: return null
        return ApiCredentials(apiId, apiHash)
    }

    companion object {
        /** The environment variable holding the numeric API id from `my.telegram.org`. */
        const val API_ID_ENV = "TG_API_ID"

        /** The environment variable holding the API hash from `my.telegram.org`. */
        const val API_HASH_ENV = "TG_API_HASH"

        /**
         * Builds the production context: the file-backed store and console output, with the real
         * Telegram client behind every use case.
         *
         * [configDir] is the `--config-dir` override; when it is null [ConfigPaths] falls back to
         * the platform default. [noColor] is the `--no-color` flag and [color] is the `--color` one,
         * which forces styling for a caller that is not a terminal; `--no-color` wins if both are
         * given. Otherwise colour is emitted only for the table format, on a terminal, with
         * `NO_COLOR` unset; the JSON and plain formats never carry escapes. The same terminal test
         * decides whether an upload shows a progress bar, which colour then shapes: a bar drawn with
         * `--no-color` is ASCII.
         */
        fun create(
            options: GlobalOptions = GlobalOptions(),
            environment: Map<String, String> = System.getenv(),
        ): AppContext {
            val (configDir, noColor, color, tableWidth) = options
            val dir = ConfigPaths(configDirOverride = configDir?.toString()).baseDir()
            val configStore = JsonConfigStore(dir)
            val format = configStore.load().outputFormat
            val terminal = System.console()?.isTerminal == true
            // A one-shot command has no JLine terminal, and building one would start a background
            // console reader that would steal stdin from `send-file -`. Instead, fix the process's
            // own stdout in place: on a Windows console at a legacy code page the JVM's encoder turns
            // non-ASCII into `?` before the terminal ever sees it. This is a strict no-op off
            // Windows, off a console, and when the console and the stream are already UTF-8 — so
            // redirected runs stay byte-identical. The interactive shell then swaps in its own
            // writer through [outputOn]; JLine renders via `WriteConsoleW` and needs none of this.
            WindowsConsoleUtf8.apply(terminal = terminal)
            val styled = colorEnabled(noColor, color, environment, terminal = terminal)
            val columns = TerminalColumns()
            // Output and the progress bar keep `System.out` here, which is now the stream the call
            // above replaced when a fix was warranted.
            return AppContext(
                configDir = dir,
                configStore = configStore,
                output = ConsoleOutput(format) { tableWidth.resolve(columns::get) },
                environment = environment,
                messageStyler = messageStylerFor(format, styled),
                outputFormat = format,
                isInteractiveTerminal = terminal,
                progressFactory = { UploadProgressBar(out = System.out, color = styled) },
                tableWidth = tableWidth,
            )
        }
    }
}

/** Raised when a command needs Telegram but neither the config nor the environment has credentials. */
class MissingCredentialsError : CliktError(
    "Telegram API credentials are not set: set them with `kotlogramme config set` " +
        "or the TG_API_ID/TG_API_HASH environment variables.",
)

private fun defaultAuthenticate(config: AppConfig): Authenticate {
    val client = clientFor(config)
    return AuthenticateService(KotlogramAccountGateway(KotlogramAccountOperations(client)))
}

private fun defaultListDialogs(config: AppConfig): ListDialogs =
    ListDialogsService(KotlogramChatGateway(KotlogramChatOperations(clientFor(config))))

private fun defaultReadHistory(config: AppConfig): ReadHistory {
    val client = clientFor(config)
    val chatOperations = KotlogramChatOperations(client)
    return ReadHistoryService(
        KotlogramMessageGateway(chatOperations, KotlogramMessageOperations(client)),
        KotlogramUserGateway(KotlogramUserOperations(client)),
    )
}

private fun defaultMessageWriter(config: AppConfig): MessageWriter {
    val client = clientFor(config)
    val chatOperations = KotlogramChatOperations(client)
    return MessageWritingService(
        KotlogramMessageWriteGateway(
            KotlogramMessageWriteOperations(client),
            ChatReferenceResolver(chatOperations),
        ),
    )
}

private fun defaultContacts(config: AppConfig): Contacts =
    ContactsService(KotlogramContactGateway(KotlogramContactOperations(clientFor(config))))

private fun defaultSearchMessages(config: AppConfig): SearchMessages {
    val client = clientFor(config)
    val chatOperations = KotlogramChatOperations(client)
    return SearchMessagesService(
        KotlogramMessageSearchGateway(KotlogramSearchOperations(client), ChatReferenceResolver(chatOperations)),
    )
}

private fun defaultChatMembers(config: AppConfig): ChatMembers {
    val client = clientFor(config)
    val chatOperations = KotlogramChatOperations(client)
    return ChatMembersService(
        KotlogramParticipantGateway(
            KotlogramParticipantOperations(client),
            ChatReferenceResolver(chatOperations),
        ),
    )
}

private fun defaultAdminRights(config: AppConfig): AdminRights {
    val client = clientFor(config)
    val chatOperations = KotlogramChatOperations(client)
    return AdminRightsService(
        KotlogramAdminRightsGateway(KotlogramAdminOperations(client), ChatReferenceResolver(chatOperations)),
    )
}

private fun defaultListen(config: AppConfig): Listen {
    val client = clientFor(config)
    val accounts = KotlogramAccountGateway(KotlogramAccountOperations(client))
    // A session belongs to one account for as long as it lasts, so its id is read once; a failed
    // read is not remembered and is tried again on the next update.
    val accountId = lazy(LazyThreadSafetyMode.PUBLICATION) { accounts.currentAccount().id }
    val userNames = UserNameCache(KotlogramUserOperations(client), SystemClock())
    val selfId = { runCatching { accountId.value }.getOrNull() }
    return ListenService(KotlogramUpdateLoop(client, userNames, selfId))
}

private fun defaultListFolders(config: AppConfig): ListFolders =
    ListFoldersService(KotlogramFolderGateway(KotlogramFolderOperations(clientFor(config))))

private fun defaultStickers(config: AppConfig): Stickers {
    val client = clientFor(config)
    return StickerService(
        KotlogramStickerGateway(
            KotlogramStickerOperations(client),
            ChatReferenceResolver(KotlogramChatOperations(client)),
        ),
    )
}

private fun defaultInline(config: AppConfig): InlineBots {
    val client = clientFor(config)
    return InlineService(
        KotlogramInlineGateway(
            KotlogramInlineOperations(client),
            ChatReferenceResolver(KotlogramChatOperations(client)),
        ),
    )
}

private fun defaultSendMedia(config: AppConfig): SendMedia = SendMediaService(mediaGateway(config))

private fun defaultDownloadMedia(config: AppConfig): DownloadMedia = DownloadMediaService(mediaGateway(config))

private fun defaultSessions(config: AppConfig): Sessions =
    SessionsService(KotlogramSessionGateway(KotlogramSessionOperations(clientFor(config))))

private fun defaultPhotos(config: AppConfig): Photos {
    val client = clientFor(config)
    val operations = KotlogramPhotoOperations(client)
    return PhotosService(KotlogramPhotoGateway(operations, ChatReferenceResolver(KotlogramChatOperations(client))))
}

/** The gateway both media commands share, so a send and a download resolve a peer the same way. */
private fun mediaGateway(config: AppConfig): MediaGateway {
    val client = clientFor(config)
    return KotlogramMediaGateway(
        KotlogramMediaOperations(client),
        ChatReferenceResolver(KotlogramChatOperations(client)),
    )
}

private fun clientFor(config: AppConfig) =
    TelegramClientFactory().create(requireNotNull(config.credentials), config.sessionPath)
