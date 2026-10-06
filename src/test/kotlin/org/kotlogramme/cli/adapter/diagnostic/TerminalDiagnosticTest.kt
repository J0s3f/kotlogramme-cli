package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TerminalDiagnosticTest {
    @Test
    fun `the real terminal libraries load without failing the check`() {
        val finding = TerminalDiagnostic().run()

        assertNotEquals(DiagnosticStatus.FAILED, finding.status, finding.detail)
        assertTrue(finding.detail.contains("JLine"), finding.detail)
        assertTrue(finding.detail.contains("Mordant"), finding.detail)
    }

    @Test
    fun `a terminal that cannot be opened is a warning because commands still work`() {
        val finding = TerminalDiagnostic(openJline = { error("no terminal provider") }).run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("no terminal provider"), finding.detail)
    }

    @Test
    fun `reports the output encoding`() {
        val finding = TerminalDiagnostic(encoding = { "UTF-8" }).run()

        assertTrue(finding.detail.contains("UTF-8"), finding.detail)
    }
}
