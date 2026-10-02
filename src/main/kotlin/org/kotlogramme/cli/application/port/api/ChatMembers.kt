package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Participant

/** List a chat's members, add one or remove one. */
interface ChatMembers {
    /**
     * Lists [limit] members.
     *
     * [cursor] continues from a previous page, as the `# next:` line printed it.
     */
    fun list(reference: String, limit: Int, cursor: String? = null): List<Participant>

    fun invite(reference: String, userReference: String)

    fun kick(reference: String, userReference: String)
}
