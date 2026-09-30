package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.PasswordRequiredException
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.application.port.spi.SignInOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlogramAccountGatewayTest {
    private val operations = FakeAccountOperations()
    private val gateway = KotlogramAccountGateway(operations)

    @Test
    fun `isAuthorized reflects the facade`() {
        assertFalse(gateway.isAuthorized())

        operations.authorized = true

        assertTrue(gateway.isAuthorized())
    }

    @Test
    fun `requestLoginCode forwards the phone number`() {
        gateway.requestLoginCode("+3312345")

        assertEquals("+3312345", operations.requestedPhoneNumber)
    }

    @Test
    fun `signIn returns the mapped account`() {
        operations.signInUser = sampleUser

        val outcome = gateway.signIn("+3312345", "12345")

        assertEquals("+3312345" to "12345", operations.signedInWith)
        assertEquals(SignInOutcome.SignedIn(sampleUser.toAccount()), outcome)
    }

    @Test
    fun `signIn reports a required password with its hint`() {
        operations.signInFailure = PasswordRequiredException("hunter2")

        assertEquals(SignInOutcome.PasswordRequired("hunter2"), gateway.signIn("+3312345", "12345"))
    }

    @Test
    fun `signIn reports a required password without a hint`() {
        operations.signInFailure = PasswordRequiredException(null)

        assertEquals(SignInOutcome.PasswordRequired(null), gateway.signIn("+3312345", "12345"))
    }

    @Test
    fun `checkPassword forwards the password and returns the mapped account`() {
        operations.passwordUser = sampleUser

        val account = gateway.checkPassword("hunter2")

        assertEquals("hunter2", operations.checkedPassword)
        assertEquals(sampleUser.toAccount(), account)
    }

    @Test
    fun `importBotAuthorization forwards the token and returns the mapped account`() {
        operations.botUser = sampleUser

        val account = gateway.importBotAuthorization("bot-token")

        assertEquals("bot-token", operations.importedBotToken)
        assertEquals(sampleUser.toAccount(), account)
    }

    @Test
    fun `signOut delegates to the facade`() {
        gateway.signOut()

        assertTrue(operations.signedOut)
    }

    @Test
    fun `currentAccount returns the mapped account`() {
        operations.meUser = sampleUser

        assertEquals(sampleUser.toAccount(), gateway.currentAccount())
    }
}

private val sampleUser = User(
    id = 42L,
    username = "j0s3f",
    firstName = "Jo",
    lastName = "Sef",
    phone = "+3312345",
)

private class FakeAccountOperations : FacadeAccountOperations {
    var authorized = false
    var requestedPhoneNumber: String? = null
    var signedInWith: Pair<String, String>? = null
    var checkedPassword: String? = null
    var importedBotToken: String? = null
    var signedOut = false
    var signInUser: User = sampleUser
    var passwordUser: User = sampleUser
    var botUser: User = sampleUser
    var meUser: User = sampleUser
    var signInFailure: PasswordRequiredException? = null

    override fun isAuthorized(): Boolean = authorized

    override fun requestLoginCode(phoneNumber: String) {
        requestedPhoneNumber = phoneNumber
    }

    override fun signIn(phoneNumber: String, phoneCode: String): User {
        signedInWith = phoneNumber to phoneCode
        signInFailure?.let { throw it }
        return signInUser
    }

    override fun checkPassword(password: String): User {
        checkedPassword = password
        return passwordUser
    }

    override fun importBotAuthorization(botToken: String): User {
        importedBotToken = botToken
        return botUser
    }

    override fun signOut() {
        signedOut = true
    }

    override fun currentUser(): User = meUser
}
