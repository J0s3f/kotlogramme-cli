package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Files
import java.nio.file.Path

/** Looks at the session file on disk; it does not open it or ask Telegram about it. */
class SessionDiagnostic(private val sessionPath: () -> Path) : Diagnostic {
    override val name = "Session"

    override fun run(): Finding {
        val path = sessionPath()
        return when {
            Files.isDirectory(path) -> Finding.failed("$path is not a file")
            Files.exists(path) -> Finding.ok("$path (${sizeOf(path)})")
            else -> Finding.warning("no session yet at $path; run `login` to create one")
        }
    }

    private fun sizeOf(path: Path): String {
        val bytes = Files.size(path)
        return if (bytes >= BYTES_PER_KIB) "${bytes / BYTES_PER_KIB} KiB" else "$bytes bytes"
    }

    private companion object {
        const val BYTES_PER_KIB = 1024
    }
}
