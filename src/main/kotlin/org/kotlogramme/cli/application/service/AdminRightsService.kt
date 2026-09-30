package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.AdminRights
import org.kotlogramme.cli.application.port.spi.AdminRightsGateway
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights

/**
 * Reads, grants and revokes a member's rights through the [AdminRightsGateway].
 *
 * A blank reference is a mistake in the invocation, so it is rejected here with a clear
 * [IllegalArgumentException] rather than sent to Telegram.
 */
class AdminRightsService(private val gateway: AdminRightsGateway) : AdminRights {
    override fun permissions(reference: String, userReference: String): ChatRights {
        requireReferences(reference, userReference)
        return gateway.permissions(reference, userReference)
    }

    override fun promote(reference: String, userReference: String, rights: ChatRights) {
        requireReferences(reference, userReference)
        gateway.setAdmin(reference, userReference, rights)
    }

    override fun restrict(reference: String, userReference: String, restrictions: ChatRestrictions) {
        requireReferences(reference, userReference)
        gateway.setBanned(reference, userReference, restrictions)
    }

    private fun requireReferences(reference: String, userReference: String) {
        require(reference.isNotBlank()) { "chat reference must not be blank" }
        require(userReference.isNotBlank()) { "user reference must not be blank" }
    }
}
