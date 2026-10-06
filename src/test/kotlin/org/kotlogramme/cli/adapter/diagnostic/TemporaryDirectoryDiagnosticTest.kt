package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemporaryDirectoryDiagnosticTest {
    @Test
    fun `a writable directory is fine and left empty`() {
        val dir = Files.createTempDirectory("kotlogramme-temp-test")

        val finding = TemporaryDirectoryDiagnostic(dir).run()

        assertEquals(DiagnosticStatus.OK, finding.status, finding.detail)
        assertEquals(0, Files.list(dir).use { it.count() })
    }

    @Test
    fun `a missing directory is failed because the native library is extracted there`() {
        val finding = TemporaryDirectoryDiagnostic(Files.createTempDirectory("x").resolve("gone")).run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("native library"), finding.detail)
    }
}
