package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramSchemaDiagnosticTest {
    @Test
    fun `loads the bundled schema and decodes a sample update with it`() {
        val finding = TelegramSchemaDiagnostic().run()

        assertEquals(DiagnosticStatus.OK, finding.status, finding.detail)
        assertTrue(finding.detail.contains("layer"), finding.detail)
        assertTrue(finding.detail.contains("decoded a sample update"), finding.detail)
    }

    @Test
    fun `fails when the sample cannot be decoded`() {
        val finding = TelegramSchemaDiagnostic(sample = byteArrayOf(1, 2, 3, 4)).run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("could not decode"), finding.detail)
    }
}
