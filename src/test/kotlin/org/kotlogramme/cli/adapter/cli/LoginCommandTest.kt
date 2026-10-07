package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.api.LoginStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test
    fun `an account that is already signed in needs no code`() {
        val authenticate = FakeAuthenticate(startStep = LoginStep.SignedIn(testAccount))
        val fixture = cliFixture(authenticate = authenticate)

        val result = fixture.run("login", "--phone", testPhone)

        assertEquals(0, result.statusCode)
        assertTrue(authenticate.submittedCodes.isEmpty())
        assertTrue(fixture.output.text.contains("Signed in as Ada Lovelace"))
    }

    @Test
    fun `a password asked for right after the phone number skips the code`() {
        val authenticate = FakeAuthenticate(
            startStep = LoginStep.PasswordRequired(hint = null),
            passwordStep = LoginStep.SignedIn(testAccount),
        )

        val result = cliFixture(authenticate = authenticate).run("login", "--phone", testPhone, "--password", "pw")

        assertEquals(0, result.statusCode)
        assertTrue(authenticate.submittedCodes.isEmpty())
        assertEquals(listOf("pw"), authenticate.submittedPasswords)
    }

    @Test
    fun `a login code Telegram does not accept is an error`() {
        val authenticate = FakeAuthenticate(codeStep = LoginStep.CodeRequired(testPhone, hint = null))

        val result = cliFixture(authenticate = authenticate).run("login", "--phone", testPhone, "--code", "00000")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("The login code was not accepted"), result.stderr)
    }

    @Test
    fun `a password Telegram does not accept is an error`() {
        val authenticate = FakeAuthenticate(
            codeStep = LoginStep.PasswordRequired(hint = null),
            passwordStep = LoginStep.PasswordRequired(hint = null),
        )

        val result = cliFixture(authenticate = authenticate)
            .run("login", "--phone", testPhone, "--code", "12345", "--password", "wrong")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("The password was not accepted"), result.stderr)
    }

    @Test
    fun `a server that asks for the code again after the password is an error`() {
        val authenticate = FakeAuthenticate(
            codeStep = LoginStep.PasswordRequired(hint = null),
            passwordStep = LoginStep.CodeRequired(testPhone, hint = null),
        )

        val result = cliFixture(authenticate = authenticate)
            .run("login", "--phone", testPhone, "--code", "12345", "--password", "pw")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("asked for a login code again"), result.stderr)
    }

    @Test
    fun `a missing phone number off a terminal says which option to pass`() {
        val authenticate = FakeAuthenticate()

        val result = cliFixture(authenticate = authenticate).run("login")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("terminal is not interactive"), result.stderr)
        assertTrue(authenticate.startedLogins.isEmpty())
    }
}
