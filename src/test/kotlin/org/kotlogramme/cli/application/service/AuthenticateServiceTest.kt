package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.LoginStep
import org.kotlogramme.cli.application.port.spi.AccountGateway
import org.kotlogramme.cli.application.port.spi.SignInOutcome
import org.kotlogramme.cli.domain.Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthenticateServiceTest {
    @Test
    fun `an authorized session reports the signed-in account`() {
        val gateway = FakeAccountGateway(authorized = true)

        val status = AuthenticateService(gateway).status()

        assertEquals(AccountStatus.SignedIn(account), status)
    }

    @Test
    fun `an unauthorized session is anonymous`() {
        val gateway = FakeAccountGateway(authorized = false)

        val status = AuthenticateService(gateway).status()

        assertEquals(AccountStatus.Anonymous, status)
    }

    @Test
    fun `starting a login requests a code and requires it`() {
        val gateway = FakeAccountGateway()

        val step = AuthenticateService(gateway).startLogin(phoneNumber)

        assertEquals(listOf(phoneNumber), gateway.requestedLoginCodes)
        assertEquals(LoginStep.CodeRequired(phoneNumber, hint = null), step)
    }

    @Test
    fun `submitting a code that signs in completes the login`() {
        val gateway = FakeAccountGateway(signInOutcome = SignInOutcome.SignedIn(account))

        val step = AuthenticateService(gateway).submitCode(phoneNumber, code)

        assertEquals(LoginStep.SignedIn(account), step)
    }

    @Test
    fun `submitting a code that needs a password forwards the hint`() {
        val gateway = FakeAccountGateway(signInOutcome = SignInOutcome.PasswordRequired(hint))

        val step = AuthenticateService(gateway).submitCode(phoneNumber, code)

        assertEquals(LoginStep.PasswordRequired(hint), step)
    }

    @Test
    fun `submitting the password completes the login`() {
        val gateway = FakeAccountGateway()

        val step = AuthenticateService(gateway).submitPassword(password)

        assertEquals(listOf(password), gateway.checkedPasswords)
        assertEquals(LoginStep.SignedIn(account), step)
    }

    @Test
    fun `a bot token logins and returns the imported account`() {
        val gateway = FakeAccountGateway()

        val result = AuthenticateService(gateway).loginWithBotToken(botToken)

        assertEquals(listOf(botToken), gateway.importedBotTokens)
        assertEquals(account, result)
    }

    @Test
    fun `logout signs the gateway out`() {
        val gateway = FakeAccountGateway()

        AuthenticateService(gateway).logout()

        assertEquals(1, gateway.signOutCount)
    }

    @Test
    fun `a phone-required failure while submitting the code propagates`() {
        val error = PhoneRequiredError()
        val gateway = FakeAccountGateway(signInError = error)

        assertFailsWith<PhoneRequiredError> {
            AuthenticateService(gateway).submitCode(phoneNumber, code)
        }
    }

    private class FakeAccountGateway(
        private val authorized: Boolean = false,
        private val signInOutcome: SignInOutcome = SignInOutcome.SignedIn(account),
        private val signInError: Throwable? = null,
    ) : AccountGateway {
        val requestedLoginCodes = mutableListOf<String>()
        val checkedPasswords = mutableListOf<String>()
        val importedBotTokens = mutableListOf<String>()
        var signOutCount = 0

        override fun isAuthorized(): Boolean = authorized

        override fun requestLoginCode(phoneNumber: String) {
            requestedLoginCodes += phoneNumber
        }

        override fun signIn(phoneNumber: String, phoneCode: String): SignInOutcome {
            signInError?.let { throw it }
            return signInOutcome
        }

        override fun checkPassword(password: String): Account {
            checkedPasswords += password
            return account
        }

        override fun importBotAuthorization(botToken: String): Account {
            importedBotTokens += botToken
            return account
        }

        override fun signOut() {
            signOutCount++
        }

        override fun currentAccount(): Account = account
    }

    private class PhoneRequiredError : RuntimeException()

    private companion object {
        val account = Account(
            id = 42,
            firstName = "Ada",
            lastName = "Lovelace",
            username = "ada",
            phoneNumber = "+15550100",
        )
        const val phoneNumber = "+15550142"
        const val code = "12345"
        const val password = "s3cret"
        const val hint = "your favourite number"
        const val botToken = "123456:ABC-DEF"
    }
}
