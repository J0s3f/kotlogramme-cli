package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.LoginStep
import org.kotlogramme.cli.application.port.spi.AccountGateway
import org.kotlogramme.cli.application.port.spi.SignInOutcome
import org.kotlogramme.cli.domain.Account

/** Signs in, reports what the caller must still supply, and exposes the current session. */
class AuthenticateService(private val gateway: AccountGateway) : Authenticate {
    override fun status(): AccountStatus =
        if (gateway.isAuthorized()) {
            AccountStatus.SignedIn(gateway.currentAccount())
        } else {
            AccountStatus.Anonymous
        }

    override fun startLogin(phoneNumber: String): LoginStep {
        gateway.requestLoginCode(phoneNumber)
        return LoginStep.CodeRequired(phoneNumber, hint = null)
    }

    override fun submitCode(phoneNumber: String, code: String): LoginStep =
        when (val outcome = gateway.signIn(phoneNumber, code)) {
            is SignInOutcome.SignedIn -> LoginStep.SignedIn(outcome.account)
            is SignInOutcome.PasswordRequired -> LoginStep.PasswordRequired(outcome.hint)
        }

    override fun submitPassword(password: String): LoginStep =
        LoginStep.SignedIn(gateway.checkPassword(password))

    override fun loginWithBotToken(botToken: String): Account = gateway.importBotAuthorization(botToken)

    override fun logout() {
        gateway.signOut()
    }
}
