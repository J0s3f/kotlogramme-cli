package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.AdminRightsGateway
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights

/**
 * The [AdminRightsGateway] backed by the kotlogramme facade.
 *
 * Both the chat and the member are resolved through the shared [ChatReferenceResolver], so a
 * `@username`, an invite link and a numeric id mean the same peers here as everywhere else in the
 * adapter. A member who is neither creator nor admin holds no rights, which is decided before the
 * granular lookup runs.
 */
internal class KotlogramAdminRightsGateway(
    private val operations: FacadeAdminOperations,
    private val resolver: ChatReferenceResolver,
) : AdminRightsGateway {
    override fun permissions(reference: String, userReference: String): ChatRights {
        val peer = resolver.resolve(reference)
        val user = resolver.resolve(userReference)
        val membership = operations.membership(peer, user)
        if (!membership.isCreator && !membership.isAdmin) {
            return ChatRights.NONE
        }
        return operations.rights(peer, user)?.toChatRights() ?: membership.toChatRights()
    }

    override fun setAdmin(reference: String, userReference: String, rights: ChatRights) {
        val peer = resolver.resolve(reference)
        val user = resolver.resolve(userReference)
        operations.setAdmin(peer, user, rights.toFacadePermissions())
    }

    override fun setBanned(reference: String, userReference: String, restrictions: ChatRestrictions) {
        val peer = resolver.resolve(reference)
        val user = resolver.resolve(userReference)
        operations.setBanned(peer, user, restrictions.toFacadeRestrictions())
    }
}
