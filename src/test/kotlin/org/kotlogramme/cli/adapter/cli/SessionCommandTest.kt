package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Session
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionCommandTest {
    private val current = Session(
        hash = 11,
        deviceModel = "Desktop",
        platform = "Windows",
        appVersion = "0.4.0",
        ip = "203.0.113.7",
        country = "AT",
        createdAt = Instant.parse("2026-01-01T12:30:00Z"),
        current = true,
    )
    private val other = current.copy(hash = 22, deviceModel = "Phone", platform = "Android", current = false)

    @Test
    fun `sessions with no subcommand lists them`() {
        val fake = FakeSessions(listOf(current, other))
        val fixture = cliFixture(sessions = fake)

        val result = fixture.run("sessions")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "current\thash\tdevice\tplatform\tapp\tip\tcountry\tcreated",
                "yes\t11\tDesktop\tWindows\t0.4.0\t203.0.113.7\tAT\t2026-01-01T12:30:00Z",
                "\t22\tPhone\tAndroid\t0.4.0\t203.0.113.7\tAT\t2026-01-01T12:30:00Z",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `sessions terminate takes the exact hash`() {
        val fake = FakeSessions()
        val fixture = cliFixture(sessions = fake)

        val result = fixture.run("sessions", "terminate", "22")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(22L), fake.terminated)
        assertEquals(listOf("Terminated session 22."), fixture.output.lines)
    }

    @Test
    fun `sessions terminate requires a hash`() {
        val fake = FakeSessions()
        val fixture = cliFixture(sessions = fake)

        val result = fixture.run("sessions", "terminate")

        assertTrue(result.statusCode != 0)
        assertEquals(emptyList(), fake.terminated)
    }

    @Test
    fun `sessions terminate-all refuses without the confirmation flag`() {
        val fake = FakeSessions()
        val fixture = cliFixture(sessions = fake)

        val result = fixture.run("sessions", "terminate-all")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--yes"), "stderr was: ${result.stderr}")
        assertEquals(0, fake.terminateAllCount)
    }

    @Test
    fun `sessions terminate-all acts with the confirmation flag`() {
        val fake = FakeSessions()
        val fixture = cliFixture(sessions = fake)

        val result = fixture.run("sessions", "terminate-all", "--yes")

        assertEquals(0, result.statusCode)
        assertEquals(1, fake.terminateAllCount)
        assertEquals(listOf("Terminated every session except the current one."), fixture.output.lines)
    }
}
