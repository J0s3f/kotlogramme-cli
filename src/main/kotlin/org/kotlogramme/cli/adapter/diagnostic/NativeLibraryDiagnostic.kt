package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.adapter.telegram.FacadeNativeLibraryProbe
import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Files

/**
 * Loads the facade's native library by building its client, which needs no credentials and no
 * session of the user's.
 *
 * The client is built with placeholder credentials against a scratch session that is deleted
 * afterwards, so the check neither depends on the configuration nor touches the real session file.
 * The client is closed at once and nothing is sent to Telegram.
 */
class NativeLibraryDiagnostic(
    private val probe: NativeLibraryProbe = FacadeNativeLibraryProbe(),
) : Diagnostic {
    override val name = "Native library"

    override fun run(): Finding {
        val scratch = Files.createTempDirectory(SCRATCH_PREFIX)
        try {
            return when (val check = probe.check(PLACEHOLDER_CREDENTIALS, scratch.resolve(SESSION_FILE))) {
                is NativeLibraryCheck.Loaded -> Finding.ok(loadedDetail(check))
                is NativeLibraryCheck.Unavailable -> Finding.failed(unavailableDetail(check))
            }
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun loadedDetail(check: NativeLibraryCheck.Loaded): String {
        val origin = check.facadeOrigin?.let { ", facade classes from $it" }.orEmpty()
        return "loaded; client created and closed without contacting Telegram$origin"
    }

    private fun unavailableDetail(check: NativeLibraryCheck.Unavailable): String =
        "could not be loaded: ${check.reason}. The jar may not contain a build for this platform, " +
            "or the temporary directory may not allow loading libraries"

    private companion object {
        const val SCRATCH_PREFIX = "kotlogramme-doctor"
        const val SESSION_FILE = "session.sqlite"
        val PLACEHOLDER_CREDENTIALS = ApiCredentials(apiId = 1, apiHash = "0")
    }
}
