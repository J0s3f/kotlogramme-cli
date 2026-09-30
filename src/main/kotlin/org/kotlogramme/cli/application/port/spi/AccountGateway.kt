package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Account

/** The result of attempting to sign in with a login code. */
sealed interface SignInOutcome {
    data class SignedIn(val account: Account) : SignInOutcome

    data class PasswordRequired(val hint: String?) : SignInOutcome
}

/**
 * The account operations the facade exposes, in domain terms.
 *
 * This is the only outbound port that touches authentication; everything above it is expressed in
 * [Account] and [SignInOutcome].
 */
interface AccountGateway {
    fun isAuthorized(): Boolean

    fun requestLoginCode(phoneNumber: String)

    fun signIn(phoneNumber: String, phoneCode: String): SignInOutcome

    fun checkPassword(password: String): Account

    fun importBotAuthorization(botToken: String): Account

    fun signOut()

    /** The account the current session belongs to. Fails if the session is not authorized. */
    fun currentAccount(): Account
}
