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
}
