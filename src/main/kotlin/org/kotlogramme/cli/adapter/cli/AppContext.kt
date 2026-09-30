package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktError
import org.kotlogramme.cli.adapter.config.ConfigPaths
import org.kotlogramme.cli.adapter.config.JsonConfigStore
import org.kotlogramme.cli.adapter.format.ConsoleOutput
import org.kotlogramme.cli.adapter.telegram.ChatReferenceResolver
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramChatGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramChatOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramContactGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramContactOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramFolderGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramFolderOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageSearchGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageWriteGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramMessageWriteOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramParticipantGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramParticipantOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramSearchOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramUpdateOperations
import org.kotlogramme.cli.adapter.telegram.KotlogramUpdateSource
import org.kotlogramme.cli.adapter.telegram.TelegramClientFactory
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.Listen
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.service.AuthenticateService
import org.kotlogramme.cli.application.service.ChatMembersService
import org.kotlogramme.cli.application.service.ContactsService
import org.kotlogramme.cli.application.service.ListDialogsService
import org.kotlogramme.cli.application.service.ListFoldersService
import org.kotlogramme.cli.application.service.ListenService
import org.kotlogramme.cli.application.service.MessageWritingService
import org.kotlogramme.cli.application.service.ReadHistoryService
import org.kotlogramme.cli.application.service.SearchMessagesService
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
    private val authenticateFactory: (AppConfig) -> Authenticate = ::defaultAuthenticate,
    private val listDialogsFactory: (AppConfig) -> ListDialogs = ::defaultListDialogs,
    private val readHistoryFactory: (AppConfig) -> ReadHistory = ::defaultReadHistory,
    private val messageWriterFactory: (AppConfig) -> MessageWriter = ::defaultMessageWriter,
    private val contactsFactory: (AppConfig) -> Contacts = ::defaultContacts,
    private val searchMessagesFactory: (AppConfig) -> SearchMessages = ::defaultSearchMessages,
    private val chatMembersFactory: (AppConfig) -> ChatMembers = ::defaultChatMembers,
    private val listFoldersFactory: (AppConfig) -> ListFolders = ::defaultListFolders,
    private val listenFactory: (AppConfig) -> Listen = ::defaultListen,
) {
    /** The configuration as it is on disk right now. */
    fun config(): AppConfig = configStore.load()

    /** Persists [config], replacing whatever was stored before. */
    fun save(config: AppConfig) {
        configStore.save(config)
    }

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

    /** The dialog-folder use case, with the same missing-credentials error as [authenticate]. */
    fun listFolders(): ListFolders = listFoldersFactory(configured())

    /** The update-following use case, with the same missing-credentials error as [authenticate]. */
    fun listen(): Listen = listenFactory(configured())

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
         * the platform default.
         */
        fun create(
            configDir: Path? = null,
            environment: Map<String, String> = System.getenv(),
        ): AppContext {
            val dir = ConfigPaths(configDirOverride = configDir?.toString()).baseDir()
            val configStore = JsonConfigStore(dir)
            return AppContext(
                configDir = dir,
                configStore = configStore,
                output = ConsoleOutput(configStore.load().outputFormat),
                environment = environment,
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

private fun defaultListen(config: AppConfig): Listen =
    ListenService(KotlogramUpdateSource(KotlogramUpdateOperations(clientFor(config))))

private fun defaultListFolders(config: AppConfig): ListFolders =
    ListFoldersService(KotlogramFolderGateway(KotlogramFolderOperations(clientFor(config))))


private fun clientFor(config: AppConfig) =
    TelegramClientFactory().create(requireNotNull(config.credentials), config.sessionPath)
