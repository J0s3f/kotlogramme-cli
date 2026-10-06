package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.Doctor
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.DiagnosticResult
import org.kotlogramme.cli.domain.Finding

/**
 * Runs every diagnostic and reports each one, in the order given.
 *
 * A check that throws, including one that cannot load a native library, is reported as failed and
 * the rest still run, because the point of a doctor is to show everything that is wrong at once.
 */
class DoctorService(private val diagnostics: List<Diagnostic>) : Doctor {
    override fun run(): List<DiagnosticResult> = diagnostics.map { diagnostic ->
        val finding = runSafely(diagnostic)
        DiagnosticResult(diagnostic.name, finding.status, finding.detail)
    }

    private fun runSafely(diagnostic: Diagnostic): Finding = try {
        diagnostic.run()
    } catch (error: Exception) {
        Finding.failed(describe(error))
    } catch (error: LinkageError) {
        Finding.failed(describe(error))
    }

    private fun describe(error: Throwable): String = error.message ?: error::class.simpleName ?: error.toString()
}
