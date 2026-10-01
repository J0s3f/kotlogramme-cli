package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.SessionGateway
import org.kotlogramme.cli.domain.Session
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionsServiceTest {
    @Test
    fun `list reads through the gateway`() {
        val gateway = FakeSessionGateway().apply { sessions = listOf(session) }

        val result = SessionsService(gateway).list()

        assertEquals(listOf(session), result)
    }

    @Test
    fun `terminate passes the hash through`() {
        val gateway = FakeSessionGateway()

        SessionsService(gateway).terminate(42L)

        assertEquals(listOf(42L), gateway.terminated)
    }

    @Test
    fun `terminate rejects a zero hash before the gateway`() {
        val gateway = FakeSessionGateway()

        assertFailsWith<IllegalArgumentException> { SessionsService(gateway).terminate(0L) }
        assertEquals(emptyList(), gateway.terminated)
    }

    @Test
    fun `terminateAll goes through the gateway`() {
        val gateway = FakeSessionGateway()

        SessionsService(gateway).terminateAll()

        assertEquals(1, gateway.terminateAllCount)
    }

    private class FakeSessionGateway : SessionGateway {
        var sessions: List<Session> = emptyList()
        val terminated = mutableListOf<Long>()
        var terminateAllCount = 0

        override fun sessions(): List<Session> = sessions

        override fun terminate(hash: Long) {
            terminated += hash
        }

        override fun terminateAll() {
            terminateAllCount++
        }
    }

    private companion object {
        val session = Session(
            hash = 42,
            deviceModel = "Desktop",
            platform = "Windows",
            appVersion = "0.4.0",
            ip = "203.0.113.7",
            country = "AT",
            createdAt = Instant.parse("2026-01-01T12:30:00Z"),
            current = true,
        )
    }
}
