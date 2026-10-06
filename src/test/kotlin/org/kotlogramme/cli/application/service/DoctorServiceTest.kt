package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.DiagnosticResult
import org.kotlogramme.cli.domain.DiagnosticStatus
import org.kotlogramme.cli.domain.Finding
import kotlin.test.Test
import kotlin.test.assertEquals

class DoctorServiceTest {
    private fun diagnostic(name: String, run: () -> Finding) = object : Diagnostic {
        override val name = name

        override fun run() = run()
    }

    @Test
    fun `reports every check in the order given`() {
        val doctor = DoctorService(
            listOf(
                diagnostic("First") { Finding.ok("fine") },
                diagnostic("Second") { Finding.warning("so-so") },
            ),
        )

        assertEquals(
            listOf(
                DiagnosticResult("First", DiagnosticStatus.OK, "fine"),
                DiagnosticResult("Second", DiagnosticStatus.WARNING, "so-so"),
            ),
            doctor.run(),
        )
    }

    @Test
    fun `a check that throws is reported as failed and the others still run`() {
        val doctor = DoctorService(
            listOf(
                diagnostic("Broken") { error("disk on fire") },
                diagnostic("Healthy") { Finding.ok("fine") },
            ),
        )

        val results = doctor.run()

        assertEquals(DiagnosticStatus.FAILED, results[0].status)
        assertEquals("disk on fire", results[0].detail)
        assertEquals(DiagnosticStatus.OK, results[1].status)
    }

    @Test
    fun `a link error in a check is reported and does not end the run`() {
        val doctor = DoctorService(
            listOf(
                diagnostic("Native") { throw UnsatisfiedLinkError("no kotlogramme in java.library.path") },
                diagnostic("Healthy") { Finding.ok("fine") },
            ),
        )

        val results = doctor.run()

        assertEquals(DiagnosticStatus.FAILED, results[0].status)
        assertEquals(true, results[0].detail.contains("no kotlogramme"), results[0].detail)
        assertEquals(2, results.size)
    }

    @Test
    fun `an error with no message still says what it was`() {
        val doctor = DoctorService(listOf(diagnostic("Odd") { throw IllegalStateException() }))

        assertEquals("IllegalStateException", doctor.run().single().detail)
    }

    @Test
    fun `no checks give an empty report`() {
        assertEquals(emptyList(), DoctorService(emptyList()).run())
    }
}
