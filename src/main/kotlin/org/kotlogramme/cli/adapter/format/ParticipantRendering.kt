package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Participant

/**
 * Renders a chat's members.
 *
 * The row is plain data so the same projection feeds every [Output] format; the role is its own
 * column so `creator` and `admin` are visible rather than encoded in the name.
 */
internal val PARTICIPANT_HEADERS = listOf("id", "name", "username", "role")

/** Prints the members as the configured output format. */
fun Output.renderParticipants(participants: List<Participant>) {
    table(PARTICIPANT_HEADERS, participants.map(::participantRow))
}

/** The row for one [participant]; pure so it can be snapshot-tested without an [Output]. */
internal fun participantRow(participant: Participant): List<String> = listOf(
    participant.id.toString(),
    participant.displayName,
    participant.username.orEmpty(),
    participant.role,
)
