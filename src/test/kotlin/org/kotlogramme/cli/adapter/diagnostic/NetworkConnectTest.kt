package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real TCP connection, against a socket this test opens, so nothing leaves the machine. */
class NetworkConnectTest {
    @Test
    fun `a port that accepts connections is reported with its time`() {
        ServerSocket(0).use { server ->
            val finding = NetworkDiagnostic(listOf(Endpoint("local", "127.0.0.1", server.localPort))).run()

            assertEquals(DiagnosticStatus.OK, finding.status, finding.detail)
            assertTrue(Regex("""local \d+ ms""").matches(finding.detail), finding.detail)
        }
    }

    @Test
    fun `a port nobody listens on is a warning that names the endpoint`() {
        val closedPort = ServerSocket(0).use { it.localPort }

        val finding = NetworkDiagnostic(listOf(Endpoint("local", "127.0.0.1", closedPort))).run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.startsWith("local: "), finding.detail)
    }
}
