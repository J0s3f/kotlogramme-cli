package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.api.LoginStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginCommandTest {
    @Test
    fun `login walks phone, code and password`() {
        val authenticate = FakeAuthenticate(
            startStep = LoginStep.CodeRequired(testPhone, hint = null),
            codeStep = LoginStep.PasswordRequired(hint = "favourite number"),
            passwordStep = LoginStep.SignedIn(testAccount),
        )
        val fixture = cliFixture(authenticate = authenticate)

        val result = fixture.run("login", "--phone", testPhone, "--code", "12345", "--password", "s3cret")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(testPhone), authenticate.startedLogins)
        assertEquals(listOf(testPhone to "12345"), authenticate.submittedCodes)
        assertEquals(listOf("s3cret"), authenticate.submittedPasswords)
        assertTrue(fixture.output.text.contains("Signed in as Ada Lovelace"))
    }

    @Test
    fun `login with a bot token imports the bot`() {
        val authenticate = FakeAuthenticate()
        val fixture = cliFixture(authenticate = authenticate)

        val result = fixture.run("login", "--bot-token", "123456:ABC-DEF")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("123456:ABC-DEF"), authenticate.botTokens)
        assertTrue(fixture.output.text.contains("Signed in as Ada Lovelace"))
    }

    @Test
    fun `a login that signs in with the code skips the password`() {
        val authenticate = FakeAuthenticate(
            startStep = LoginStep.CodeRequired(testPhone, hint = null),
            codeStep = LoginStep.SignedIn(testAccount),
        )
        val fixture = cliFixture(authenticate = authenticate)

        val result = fixture.run("login", "--phone", testPhone, "--code", "12345")

        assertEquals(0, result.statusCode)
        assertTrue(authenticate.submittedPasswords.isEmpty())
        assertTrue(fixture.output.text.contains("Signed in as Ada Lovelace"))
    }
}
