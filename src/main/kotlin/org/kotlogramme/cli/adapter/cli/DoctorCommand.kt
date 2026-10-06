package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.requireObject
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.application.service.DoctorService
import org.kotlogramme.cli.domain.DiagnosticStatus

/**
 * Checks the installation and its environment, one row per check.
 *
 * The checks that need nothing from the user, such as loading the native library and the terminal
 * libraries, always run, so the command is useful before any API credentials exist. The ones that
 * need credentials run when they are set and are listed as skipped otherwise. Every check is
 * reported even when an earlier one fails, and the command exits with an error only when a check
 * failed; a warning or a skipped check is not a failure.
 */
class DoctorCommand(
    private val diagnostics: (AppContext) -> List<Diagnostic> = ::standardDiagnostics,
) : CliktCommand(name = "doctor") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        val results = DoctorService(diagnostics(appContext)).run()
        appContext.output.table(HEADERS, results.map { listOf(it.check, it.status.label, it.detail) })

        val failed = results.count { it.status == DiagnosticStatus.FAILED }
        if (failed > 0) throw CliktError("$failed of ${results.size} checks failed.")
    }

    private companion object {
        val HEADERS = listOf("check", "status", "detail")
    }
}
