package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Account
import org.kotlogramme.cli.domain.Finding

/**
 * Asks Telegram who the session belongs to, when API credentials are set.
 *
 * Without credentials the check is skipped rather than failed, so `doctor` is useful on a machine
 * that has not been set up yet. With them it proves the credentials, the network and the session
 * all work together, which no offline check can.
 */
class AccountDiagnostic(
    private val hasCredentials: () -> Boolean,
    private val status: () -> AccountStatus,
) : Diagnostic {
    override val name = "Account"

    override fun run(): Finding {
        if (!hasCredentials()) return Finding.skipped("needs API credentials")
        return runCatching { status() }.fold(
            onSuccess = ::describe,
            onFailure = { Finding.failed("could not ask Telegram with these credentials: ${it.message ?: it}") },
        )
    }

    private fun describe(status: AccountStatus): Finding = when (status) {
        AccountStatus.Anonymous -> Finding.warning("the session is not signed in; run `login`")
        is AccountStatus.SignedIn -> Finding.ok("signed in as ${status.account.label()}")
    }

    private fun Account.label(): String {
        val handle = username?.let { "@$it, " }.orEmpty()
        return "$displayName (${handle}id $id)"
    }
}
