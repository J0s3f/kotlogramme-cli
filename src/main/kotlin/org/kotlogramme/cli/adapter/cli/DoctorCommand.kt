package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.requireObject
import org.kotlogramme.cli.adapter.telegram.FacadeNativeLibraryProbe
import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe

/**
 * Checks that this installation can load the facade's native library.
 *
 * The facade client is built only to force the native library load and is closed immediately; the
 * command issues no network query. When the library is missing or incompatible the failure is
 * reported as a one-line error, never as an `UnsatisfiedLinkError` stack trace.
 */
class DoctorCommand(
    private val probe: NativeLibraryProbe = FacadeNativeLibraryProbe(),
) : CliktCommand(name = "doctor") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        val config = appContext.config()
        val credentials = appContext.credentials(config) ?: throw MissingCredentialsError()

        appContext.output.line("Doctor: checking the local installation.")
        appContext.output.line("Credentials: set (API id ${credentials.apiId})")
        appContext.output.line("Session path: ${config.sessionPath}")

        when (val check = probe.check(credentials, config.sessionPath)) {
            is NativeLibraryCheck.Loaded -> reportLoaded(check)
            is NativeLibraryCheck.Unavailable -> throw CliktError(unavailableMessage(check))
        }
    }

    private fun reportLoaded(check: NativeLibraryCheck.Loaded) {
        appContext.output.line("Native library: loaded")
        check.facadeOrigin?.let { appContext.output.line("Facade classes: $it") }
        appContext.output.line("Client: created and closed without a network query.")
    }

    private fun unavailableMessage(check: NativeLibraryCheck.Unavailable): String =
        "The kotlogramme native library could not be loaded: ${check.reason}. " +
            "The jar may not contain a build for this platform, or the session directory " +
            "may not be writable."
}
