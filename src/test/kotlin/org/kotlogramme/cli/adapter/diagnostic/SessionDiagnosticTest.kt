package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionDiagnosticTest {
    private fun tempDirectory(): Path = Files.createTempDirectory("kotlogramme-session-test")

    @Test
    fun `no session file yet is a warning that says how to log in`() {
        val finding = SessionDiagnostic { tempDirectory().resolve("session.sqlite") }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("login"), finding.detail)
    }

    @Test
    fun `an existing session is fine and its size is shown`() {
        val session = tempDirectory().resolve("session.sqlite")
        Files.write(session, ByteArray(2048))

        val finding = SessionDiagnostic { session }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("2 KiB"), finding.detail)
    }

    @Test
    fun `a session path that is a directory is failed`() {
        val finding = SessionDiagnostic { tempDirectory() }.run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("not a file"), finding.detail)
    }

    @Test
    fun `a session whose directory does not exist is a warning`() {
        val finding = SessionDiagnostic { tempDirectory().resolve("missing/session.sqlite") }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
    }
}
