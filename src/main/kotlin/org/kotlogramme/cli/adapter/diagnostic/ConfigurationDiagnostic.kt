package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Files
import java.nio.file.Path

/** Checks that the configuration can be read and that its directory can be written to, or created. */
class ConfigurationDiagnostic(
    private val configDir: Path,
    private val load: () -> AppConfig,
) : Diagnostic {
    override val name = "Configuration"

    override fun run(): Finding {
        runCatching { load() }.onFailure { error ->
            return Finding.failed("the configuration cannot be read: ${error.message ?: error}")
        }
        return runCatching { probeWritable() }.fold(
            onSuccess = { Finding.ok("$configDir ($directoryState, config valid)") },
            onFailure = { error -> Finding.failed("$configDir is not writable: ${error.message ?: error}") },
        )
    }

    private val directoryState: String
        get() = if (Files.exists(configDir)) "writable" else "created on first use"

    /** A directory that does not exist yet is checked through the nearest one that does, and is not created. */
    private fun probeWritable() {
        val existing = generateSequence(configDir.toAbsolutePath()) { it.parent }.first { Files.exists(it) }
        Files.delete(Files.createTempFile(existing, "doctor", ".tmp"))
    }
}
