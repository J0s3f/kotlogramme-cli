package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights

/** Read a member's rights, promote them, or restrict them. */
interface AdminRights {
    fun permissions(reference: String, userReference: String): ChatRights

    fun promote(reference: String, userReference: String, rights: ChatRights)

    fun restrict(reference: String, userReference: String, restrictions: ChatRestrictions)
}
