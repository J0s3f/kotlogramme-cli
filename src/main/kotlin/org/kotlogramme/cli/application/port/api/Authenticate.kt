package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Account

/** The next thing the caller must supply to finish signing in. */
sealed interface LoginStep {
    data class CodeRequired(val phoneNumber: String, val hint: String?) : LoginStep

    data class PasswordRequired(val hint: String?) : LoginStep

    data class SignedIn(val account: Account) : LoginStep
}

/** Whether a session is currently authorized. */
sealed interface AccountStatus {
    data object Anonymous : AccountStatus

    data class SignedIn(val account: Account) : AccountStatus
}

/**
 * The inbound port for authentication.
 *
 * The methods are deliberately stateless snapshots of a multi-step flow: the caller holds the
 * phone number between [startLogin] and [submitCode], so the service stays easy to test and the
 * interactive and one-shot modes share the same sequence.
 */
interface Authenticate {
    fun status(): AccountStatus

    fun startLogin(phoneNumber: String): LoginStep

    fun submitCode(phoneNumber: String, code: String): LoginStep

    fun submitPassword(password: String): LoginStep

    fun loginWithBotToken(botToken: String): Account

    fun logout()
}
