package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Files
import java.nio.file.Path

/** Checks that the configuration can be read and that its directory can be written to. */
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
            onSuccess = { Finding.ok("$configDir is writable and the configuration is valid") },
            onFailure = { error -> Finding.failed("$configDir is not writable: ${error.message ?: error}") },
        )
    }

    private fun probeWritable() {
        Files.delete(Files.createTempFile(configDir, "doctor", ".tmp"))
    }
}
