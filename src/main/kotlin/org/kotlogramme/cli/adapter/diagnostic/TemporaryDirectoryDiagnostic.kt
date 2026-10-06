package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Files
import java.nio.file.Path

/** Checks that the temporary directory can be written to, because the native library is extracted there. */
class TemporaryDirectoryDiagnostic(private val directory: Path) : Diagnostic {
    override val name = "Temporary directory"

    override fun run(): Finding = runCatching {
        Files.delete(Files.createTempFile(directory, "doctor", ".tmp"))
    }.fold(
        onSuccess = { Finding.ok("$directory is writable") },
        onFailure = { error ->
            Finding.failed(
                "$directory is not usable: ${error.message ?: error}; " +
                    "the native library is extracted there when the client starts",
            )
        },
    )
}
