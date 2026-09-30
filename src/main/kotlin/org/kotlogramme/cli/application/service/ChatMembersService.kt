package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.spi.ParticipantGateway
import org.kotlogramme.cli.domain.Participant

/** Lists a chat's members and removes one, validating the arguments before calling the gateway. */
class ChatMembersService(private val gateway: ParticipantGateway) : ChatMembers {
    override fun list(reference: String, limit: Int): List<Participant> {
        require(limit > 0) { "limit must be positive but was $limit" }
        return gateway.participants(reference, limit)
    }

    override fun kick(reference: String, userReference: String) {
        require(userReference.isNotBlank()) { "user reference must not be blank" }
        gateway.kick(reference, userReference)
    }
}
