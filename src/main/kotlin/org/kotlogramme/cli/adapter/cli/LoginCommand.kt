package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.ConversionResult
import com.github.ajalt.mordant.terminal.Prompt
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.LoginStep
import org.kotlogramme.cli.domain.Account

/**
 * Signs in with a phone number (code, then optional two-factor password), or with a bot token.
 *
 * Every input can be given as an option so scripts drive it non-interactively; whatever is missing
 * is prompted for on an interactive terminal.
 */
class LoginCommand : CliktCommand(name = "login") {
    private val appContext by requireObject<AppContext>()

    private val phone by option("--phone", help = "Phone number in international format")
    private val code by option("--code", help = "The login code Telegram sent")
    private val password by option("--password", help = "The two-factor password, if the account has one")
    private val botToken by option("--bot-token", help = "Sign in as a bot instead of a user")

    override fun run() {
        val authenticate = appContext.authenticate()
        val account = botToken?.let(authenticate::loginWithBotToken) ?: signInUser(authenticate)
        appContext.output.printAccount(account)
    }

    private fun signInUser(authenticate: Authenticate): Account {
        val phoneNumber = phone ?: prompt("Phone number")
        return when (val step = authenticate.startLogin(phoneNumber)) {
            is LoginStep.SignedIn -> step.account
            is LoginStep.CodeRequired -> completeCode(authenticate, phoneNumber)
            is LoginStep.PasswordRequired -> completePassword(authenticate)
        }
    }

    private fun completeCode(authenticate: Authenticate, phoneNumber: String): Account {
        val loginCode = code ?: prompt("Login code")
        return when (val step = authenticate.submitCode(phoneNumber, loginCode)) {
            is LoginStep.SignedIn -> step.account
            is LoginStep.PasswordRequired -> completePassword(authenticate)
            is LoginStep.CodeRequired -> throw CliktError("The login code was not accepted; try again.")
        }
    }

    private fun completePassword(authenticate: Authenticate): Account {
        val secret = password ?: prompt("Password", hideInput = true)
        return when (val step = authenticate.submitPassword(secret)) {
            is LoginStep.SignedIn -> step.account
            is LoginStep.PasswordRequired -> throw CliktError("The password was not accepted; try again.")
            is LoginStep.CodeRequired -> throw CliktError("The server asked for a login code again; try again.")
        }
    }

    private fun prompt(message: String, hideInput: Boolean = false): String {
        if (!terminal.terminalInfo.inputInteractive) {
            throw UsageError("Missing input and the terminal is not interactive; pass the matching option.")
        }
        val answer = object : Prompt<String>(
            prompt = message,
            terminal = terminal,
            hideInput = hideInput,
        ) {
            override fun convert(input: String): ConversionResult<String> = ConversionResult.Valid(input)
        }.ask()
        return answer ?: throw CliktError("No input was provided for '$message'.")
    }
}
