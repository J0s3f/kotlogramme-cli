package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Participant

/** The chat membership operations the facade exposes, in domain terms. */
interface ParticipantGateway {
    /**
     * Lists [limit] members.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it.
     */
    fun participants(reference: String, limit: Int, cursor: String? = null): List<Participant>

    /** Adds [userReference] to the chat [reference] names. */
    fun invite(reference: String, userReference: String)

    /** Removes [userReference] from the chat [reference] names. */
    fun kick(reference: String, userReference: String)
}
