package org.kotlogramme.cli.adapter.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the platform-specific directory that holds kotlogramme's configuration.
 *
 * The OS name, environment and home directory are injected so every rule can be exercised on any
 * machine. [configDirOverride] lets the application honour `--config-dir` (and tests point at a
 * temporary directory) through the [CONFIG_DIR_PROPERTY] system property.
 */
class ConfigPaths(
    private val osName: String = System.getProperty("os.name"),
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: Path = Paths.get(System.getProperty("user.home")),
    private val configDirOverride: String? = System.getProperty(CONFIG_DIR_PROPERTY),
) {
    /** The directory that holds `config.json` and the default session file. */
    fun baseDir(): Path =
        configDirOverride?.let(Paths::get) ?: platformDir().resolve(APP_DIR_NAME)

    private fun platformDir(): Path = when {
        osName.contains("win", ignoreCase = true) -> appDataDir()
        osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true) ->
            userHome.resolve("Library/Application Support")
        else -> xdgConfigHome()
    }

    private fun appDataDir(): Path =
        environment["APPDATA"]?.let(Paths::get) ?: userHome.resolve("AppData/Roaming")

    private fun xdgConfigHome(): Path =
        environment["XDG_CONFIG_HOME"]?.let(Paths::get) ?: userHome.resolve(".config")

    companion object {
        /** Names the directory below the platform's configuration root. */
        const val APP_DIR_NAME = "kotlogramme"

        /** The system property that overrides the resolved base directory. */
        const val CONFIG_DIR_PROPERTY = "kotlogramme.configDir"
    }
}
