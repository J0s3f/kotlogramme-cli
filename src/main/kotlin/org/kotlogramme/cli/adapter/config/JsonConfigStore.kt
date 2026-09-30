package org.kotlogramme.cli.adapter.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/** Reads and writes [baseDir]/config.json, creating it on first use. */
class JsonConfigStore(private val baseDir: Path) : ConfigStore {

    override fun load(): AppConfig {
        val file = configFile()
        if (Files.notExists(file)) {
            Files.createDirectories(baseDir)
            return defaultConfig()
        }
        val model = decode(Files.readString(file), file)
        return model.toAppConfig(baseDir.resolve(DEFAULT_SESSION_FILE))
    }

    override fun save(config: AppConfig) {
        Files.createDirectories(baseDir)
        val target = configFile()
        val temp = Files.createTempFile(baseDir, "config", ".json.tmp")
        Files.writeString(temp, json.encodeToString(ConfigFile.from(config)))
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun configFile(): Path = baseDir.resolve(FILE_NAME)

    private fun defaultConfig(): AppConfig = AppConfig(
        credentials = null,
        sessionPath = baseDir.resolve(DEFAULT_SESSION_FILE),
        outputFormat = OutputFormat.TABLE,
    )

    private fun decode(text: String, file: Path): ConfigFile = try {
        json.decodeFromString(text)
    } catch (e: SerializationException) {
        throw IllegalStateException("Cannot read the configuration file $file: ${e.message}", e)
    }

    private companion object {
        const val FILE_NAME = "config.json"
        const val DEFAULT_SESSION_FILE = "session.sqlite"

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

/**
 * The on-disk shape of the configuration. Kept separate from [AppConfig] because [Path] and the
 * domain types have no stable JSON representation of their own.
 */
@Serializable
private data class ConfigFile(
    val credentials: CredentialsFile? = null,
    val sessionPath: String? = null,
    val outputFormat: OutputFormat = OutputFormat.TABLE,
) {
    fun toAppConfig(defaultSessionPath: Path): AppConfig = AppConfig(
        credentials = credentials?.let { ApiCredentials(it.apiId, it.apiHash) },
        sessionPath = sessionPath?.let(Paths::get) ?: defaultSessionPath,
        outputFormat = outputFormat,
    )

    companion object {
        fun from(config: AppConfig): ConfigFile = ConfigFile(
            credentials = config.credentials?.let { CredentialsFile(it.apiId, it.apiHash) },
            sessionPath = config.sessionPath.toString(),
            outputFormat = config.outputFormat,
        )
    }
}

@Serializable
private data class CredentialsFile(
    val apiId: Int,
    val apiHash: String,
)
