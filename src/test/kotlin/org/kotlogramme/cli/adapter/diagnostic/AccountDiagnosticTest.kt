package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.domain.Account
import org.kotlogramme.cli.domain.DiagnosticStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDiagnosticTest {
    private val account = Account(
        id = 291049397,
        firstName = "Joe",
        lastName = "",
        username = "joe",
        phoneNumber = null,
    )

    @Test
    fun `without credentials the check is skipped and asks for nothing`() {
        val finding = AccountDiagnostic(hasCredentials = { false }) { error("must not be asked") }.run()

        assertEquals(DiagnosticStatus.SKIPPED, finding.status)
        assertTrue(finding.detail.contains("credentials"), finding.detail)
    }

    @Test
    fun `a signed in session names the account`() {
        val finding = AccountDiagnostic(hasCredentials = { true }) { AccountStatus.SignedIn(account) }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("Joe"), finding.detail)
        assertTrue(finding.detail.contains("@joe"), finding.detail)
        assertTrue(finding.detail.contains("291049397"), finding.detail)
    }

    @Test
    fun `an account without a username is still named by its id`() {
        val anonymous = account.copy(username = null)

        val finding = AccountDiagnostic(hasCredentials = { true }) { AccountStatus.SignedIn(anonymous) }.run()

        assertTrue(finding.detail.contains("291049397"), finding.detail)
    }

    @Test
    fun `a session that is not signed in is a warning that says how to log in`() {
        val finding = AccountDiagnostic(hasCredentials = { true }) { AccountStatus.Anonymous }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("login"), finding.detail)
    }

    @Test
    fun `a failing call to Telegram is failed with the reason`() {
        val finding = AccountDiagnostic(hasCredentials = { true }) { error("API_ID_INVALID") }.run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("API_ID_INVALID"), finding.detail)
    }
}
