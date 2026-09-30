package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights

/** The admin and ban operations the facade exposes, in domain terms. */
interface AdminRightsGateway {
    fun permissions(reference: String, userReference: String): ChatRights

    fun setAdmin(reference: String, userReference: String, rights: ChatRights)

    fun setBanned(reference: String, userReference: String, restrictions: ChatRestrictions)
}
