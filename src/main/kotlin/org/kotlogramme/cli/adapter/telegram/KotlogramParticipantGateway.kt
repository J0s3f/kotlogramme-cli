package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.ParticipantGateway
import org.kotlogramme.cli.domain.Participant

/**
 * The [ParticipantGateway] backed by the kotlogramme facade.
 *
 * Both the chat and the removed user are resolved through the shared [ChatReferenceResolver], so a
 * `@username`, an invite link and a numeric id mean the same peer here as everywhere else in the
 * adapter.
 */
internal class KotlogramParticipantGateway(
    private val operations: FacadeParticipantOperations,
    private val resolver: ChatReferenceResolver,
) : ParticipantGateway {
    override fun participants(reference: String, limit: Int): List<Participant> =
        operations.participants(resolver.resolve(reference), limit)
            .map { it.toParticipant() }

    override fun kick(reference: String, userReference: String) =
        operations.kick(resolver.resolve(reference), resolver.resolve(userReference))
}
