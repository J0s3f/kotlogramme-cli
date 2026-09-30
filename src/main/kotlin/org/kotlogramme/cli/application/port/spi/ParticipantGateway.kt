package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Participant

/** The chat membership operations the facade exposes, in domain terms. */
interface ParticipantGateway {
    fun participants(reference: String, limit: Int): List<Participant>

    /** Adds [userReference] to the chat [reference] names. */
    fun invite(reference: String, userReference: String)

    /** Removes [userReference] from the chat [reference] names. */
    fun kick(reference: String, userReference: String)
}
