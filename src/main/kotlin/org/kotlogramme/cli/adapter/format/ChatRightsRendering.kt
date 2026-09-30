package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.ChatRights

/**
 * Renders a member's admin rights.
 *
 * One row per right keeps the granted and denied set visible in every [Output] format, and the
 * status is spelled out rather than encoded as a tick so a plain-text pipe stays readable.
 */
internal val CHAT_RIGHTS_HEADERS = listOf("right", "status")

/** Prints the admin rights as the configured output format. */
fun Output.renderChatRights(rights: ChatRights) {
    table(CHAT_RIGHTS_HEADERS, chatRightsRows(rights))
}

/** The rows for [rights]; pure so it can be snapshot-tested without an [Output]. */
internal fun chatRightsRows(rights: ChatRights): List<List<String>> = listOf(
    "change-info" to rights.changeInfo,
    "post-messages" to rights.postMessages,
    "edit-messages" to rights.editMessages,
    "delete-messages" to rights.deleteMessages,
    "ban-users" to rights.banUsers,
    "invite-users" to rights.inviteUsers,
    "pin-messages" to rights.pinMessages,
    "add-admins" to rights.addAdmins,
    "anonymous" to rights.anonymous,
    "manage-call" to rights.manageCall,
).map { (name, granted) -> listOf(name, if (granted) GRANTED else DENIED) }

private const val GRANTED = "granted"
private const val DENIED = "denied"
