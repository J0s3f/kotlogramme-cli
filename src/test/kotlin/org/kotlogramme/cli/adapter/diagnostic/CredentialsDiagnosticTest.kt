package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.domain.DiagnosticStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialsDiagnosticTest {
    private val validHash = "0123456789abcdef0123456789abcdef"

    @Test
    fun `set credentials show the api id and never the hash`() {
        val finding = CredentialsDiagnostic { ApiCredentials(12345, validHash) }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("12345"), finding.detail)
        assertFalse(finding.detail.contains(validHash), finding.detail)
    }

    @Test
    fun `missing credentials are a warning that says how to set them`() {
        val finding = CredentialsDiagnostic { null }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("TG_API_ID"), finding.detail)
        assertTrue(finding.detail.contains("config set"), finding.detail)
    }

    @Test
    fun `a hash that is not 32 hex characters is a warning and is not echoed`() {
        val finding = CredentialsDiagnostic { ApiCredentials(12345, "not-a-real-hash") }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("32 hex"), finding.detail)
        assertFalse(finding.detail.contains("not-a-real-hash"), finding.detail)
    }

    @Test
    fun `an api id that is not positive is a warning`() {
        val finding = CredentialsDiagnostic { ApiCredentials(0, validHash) }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("API id"), finding.detail)
    }
}
