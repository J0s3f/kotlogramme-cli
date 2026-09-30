package org.kotlogramme.cli.application.port.spi

import java.nio.file.Path

/** How command output is rendered. */
enum class OutputFormat {
    TABLE,
    PLAIN,
    JSON,
}

/** The API id and hash from `my.telegram.org`, without which no client can be created. */
data class ApiCredentials(
    val apiId: Int,
    val apiHash: String,
)

/**
 * Everything the application needs to run, resolved once at the composition root.
 *
 * [sessionPath] is the grammers SQLite session file: the facade persists the whole session there,
 * so the application never handles session internals.
 */
data class AppConfig(
    val credentials: ApiCredentials?,
    val sessionPath: Path,
    val outputFormat: OutputFormat = OutputFormat.TABLE,
)

/** Reads and writes the application configuration. */
interface ConfigStore {
    fun load(): AppConfig

    fun save(config: AppConfig)
}
