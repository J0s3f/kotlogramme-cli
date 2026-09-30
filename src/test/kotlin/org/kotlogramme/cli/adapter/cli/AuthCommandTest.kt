package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.spi.AppConfig
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthCommandTest {
    @Test
    fun `whoami prints the signed-in account`() {
        val fixture = cliFixture(authenticate = FakeAuthenticate(status = AccountStatus.SignedIn(testAccount)))

        val result = fixture.run("whoami")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf("Signed in as Ada Lovelace", "id: 42", "username: @ada", "phone: +15550100"),
            fixture.output.lines,
        )
    }

    @Test
    fun `whoami says when the session is anonymous`() {
        val fixture = cliFixture(authenticate = FakeAuthenticate(status = AccountStatus.Anonymous))

        val result = fixture.run("whoami")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("Not signed in."), fixture.output.lines)
    }

    @Test
    fun `a command that needs Telegram explains how to configure credentials`() {
        val fixture = cliFixture(
            config = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite")),
            environment = emptyMap(),
        )

        val result = fixture.run("whoami")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("kotlogramme config set"), "stderr was: ${result.stderr}")
        assertTrue(result.stderr.contains("TG_API_ID/TG_API_HASH"), "stderr was: ${result.stderr}")
        assertTrue(fixture.output.lines.isEmpty())
    }

    @Test
    fun `logout signs the session out`() {
        val authenticate = FakeAuthenticate(status = AccountStatus.SignedIn(testAccount))
        val fixture = cliFixture(authenticate = authenticate)

        val result = fixture.run("logout")

        assertEquals(0, result.statusCode)
        assertEquals(1, authenticate.logoutCount)
        assertEquals(listOf("Signed out."), fixture.output.lines)
    }
}
