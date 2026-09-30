package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktError
import org.kotlogramme.cli.adapter.config.ConfigPaths
import org.kotlogramme.cli.adapter.config.JsonConfigStore
import org.kotlogramme.cli.adapter.format.ConsoleOutput
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountGateway
import org.kotlogramme.cli.adapter.telegram.KotlogramAccountOperations
import org.kotlogramme.cli.adapter.telegram.TelegramClientFactory
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.application.service.AuthenticateService
import java.nio.file.Path

/**
 * The composition root: every dependency a command needs, built in one place.
 *
 * The config directory, the store and the output are resolved once; the [Authenticate] use case is
 * built on demand because it creates a Telegram client, which should only happen for commands that
 * actually talk to Telegram. The factories are constructor parameters so tests can swap in fakes.
 */
class AppContext(
    /** The directory that holds `config.json` and the default session file. */
    val configDir: Path,
    private val configStore: ConfigStore,
    val output: Output,
    private val environment: Map<String, String>,
    private val authenticateFactory: (AppConfig) -> Authenticate = ::defaultAuthenticate,
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
    fun authenticate(): Authenticate {
        val config = config()
        val credentials = credentials(config) ?: throw MissingCredentialsError()
        return authenticateFactory(config.copy(credentials = credentials))
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
         * Telegram client behind [Authenticate].
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
    val credentials = requireNotNull(config.credentials)
    val client = TelegramClientFactory().create(credentials, config.sessionPath)
    return AuthenticateService(KotlogramAccountGateway(KotlogramAccountOperations(client)))
}
