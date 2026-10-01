package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Session
import java.time.format.DateTimeFormatter

/**
 * Renders the active-session listing.
 *
 * The row is plain data so the same projection feeds every [Output] format. `current` is its own
 * column rather than decoration, and `hash` is shown so the value `sessions terminate` takes can be
 * copied from the listing; the hash is only ever a handle, never a secret.
 */
internal val SESSION_HEADERS =
    listOf("current", "hash", "device", "platform", "app", "ip", "country", "created")

/** Prints the sessions as the configured output format. */
fun Output.renderSessions(sessions: List<Session>) {
    table(SESSION_HEADERS, sessions.map(::sessionRow))
}

/** The row for one [session]; pure so it can be snapshot-tested without an [Output]. */
internal fun sessionRow(session: Session): List<String> = listOf(
    if (session.current) "yes" else "",
    session.hash.toString(),
    session.device,
    session.platform,
    session.appVersion,
    session.ip,
    session.country,
    DateTimeFormatter.ISO_INSTANT.format(session.createdAt),
)
