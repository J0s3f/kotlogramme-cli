package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Participant

/** List a chat's members and remove one. */
interface ChatMembers {
    fun list(reference: String, limit: Int): List<Participant>

    fun kick(reference: String, userReference: String)
}
