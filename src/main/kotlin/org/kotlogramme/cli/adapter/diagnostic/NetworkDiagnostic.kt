package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration

/**
 * A host and port to try a TCP connection to, with the name it is listed under.
 *
 * A [fallbackHosts] entry is tried only when the one before it fails, so an endpoint can be reached by
 * name and still be checked from a fixed address when the name does not resolve.
 */
data class Endpoint(val name: String, val host: String, val port: Int, val fallbackHosts: List<String> = emptyList())

/**
 * Opens a TCP connection to each endpoint and reports how long it took.
 *
 * Only a connection is made; nothing is sent. Being unreachable is a warning and not a failure,
 * because a client is still installed correctly when the machine happens to be offline.
 */
class NetworkDiagnostic(
    private val endpoints: List<Endpoint>,
    private val connect: (host: String, port: Int) -> Duration = ::tcpConnect,
) : Diagnostic {
    override val name = "Network"

    override fun run(): Finding {
        val outcomes = endpoints.map { endpoint -> endpoint to reach(endpoint) }
        val unreachable = outcomes.filter { it.second.isFailure }
        val detail = outcomes.joinToString("; ") { (endpoint, outcome) -> describe(endpoint, outcome) }
        return if (unreachable.isEmpty()) Finding.ok(detail) else Finding.warning(detail)
    }

    /** The first host to answer, or the first host's failure when none does. */
    private fun reach(endpoint: Endpoint): Result<Duration> {
        val failures = mutableListOf<Result<Duration>>()
        for (host in listOf(endpoint.host) + endpoint.fallbackHosts) {
            val attempt = runCatching { connect(host, endpoint.port) }
            if (attempt.isSuccess) return attempt
            failures += attempt
        }
        return failures.first()
    }

    private fun describe(endpoint: Endpoint, outcome: Result<Duration>): String =
        outcome.fold(
            onSuccess = { "${endpoint.name} ${it.toMillis()} ms" },
            onFailure = { "${endpoint.name}: ${it.message ?: it::class.simpleName}" },
        )
}

private const val CONNECT_TIMEOUT_MILLIS = 3_000

private fun tcpConnect(host: String, port: Int): Duration {
    val started = System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS) }
    return Duration.ofNanos(System.nanoTime() - started)
}
