package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkDiagnosticTest {
    private val targets = listOf(
        Endpoint("Telegram DC2", "149.154.167.51", 443),
        Endpoint("telegram.org", "telegram.org", 443),
    )

    @Test
    fun `reachable endpoints are fine and show how long they took`() {
        val finding = NetworkDiagnostic(targets) { _, _ -> Duration.ofMillis(38) }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("Telegram DC2 38 ms"), finding.detail)
        assertTrue(finding.detail.contains("telegram.org 38 ms"), finding.detail)
    }

    @Test
    fun `an unreachable endpoint is a warning that names it and the reason`() {
        val finding = NetworkDiagnostic(targets) { host, _ ->
            if (host == "telegram.org") throw IOException("unknown host") else Duration.ofMillis(10)
        }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("telegram.org: unknown host"), finding.detail)
        assertTrue(finding.detail.contains("Telegram DC2 10 ms"), finding.detail)
    }

    @Test
    fun `being offline is a warning and not a failure`() {
        val finding = NetworkDiagnostic(targets) { _, _ -> throw IOException("network unreachable") }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
    }

    @Test
    fun `an exception without a message still names its type`() {
        val finding = NetworkDiagnostic(targets.take(1)) { _, _ -> throw IOException() }.run()

        assertTrue(finding.detail.contains("IOException"), finding.detail)
    }

    @Test
    fun `a name that does not resolve falls back to the next host of the endpoint`() {
        val endpoint = Endpoint("DC2", "dc2.example.org", 443, fallbackHosts = listOf("192.0.2.1"))

        val finding = NetworkDiagnostic(listOf(endpoint)) { host, _ ->
            if (host == "192.0.2.1") Duration.ofMillis(12) else throw IOException("unknown host")
        }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertEquals("DC2 12 ms", finding.detail)
    }

    @Test
    fun `an endpoint whose every host fails reports the first reason`() {
        val endpoint = Endpoint("DC2", "dc2.example.org", 443, fallbackHosts = listOf("192.0.2.1"))

        val finding = NetworkDiagnostic(listOf(endpoint)) { host, _ -> throw IOException("no route to $host") }.run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertEquals("DC2: no route to dc2.example.org", finding.detail)
    }

    @Test
    fun `the first host that answers is the only one tried`() {
        val tried = mutableListOf<String>()
        val endpoint = Endpoint("DC2", "dc2.example.org", 443, fallbackHosts = listOf("192.0.2.1"))

        NetworkDiagnostic(listOf(endpoint)) { host, _ -> tried += host; Duration.ofMillis(1) }.run()

        assertEquals(listOf("dc2.example.org"), tried)
    }
}
