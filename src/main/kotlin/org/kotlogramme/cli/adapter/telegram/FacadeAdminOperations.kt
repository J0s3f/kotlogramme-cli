package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions
import com.github.badoualy.telegram.api.ChatRestrictions
import com.github.badoualy.telegram.api.ParticipantPermissions
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade admin and ban calls the admin-rights gateway needs, narrowed to a seam a test can
 * implement.
 *
 * This exists so [KotlogramAdminRightsGateway] can be exercised without a live client. Reference
 * resolution is left to the gateway, which shares the adapter's [ChatReferenceResolver]; these
 * methods take facade peers only. The facade answers a member's rights in two steps: a role check
 * that works for any member, and the participant listing that carries the granular admin rights.
 */
internal interface FacadeAdminOperations {
    /** Reports what [user] is in [peer], which is `channelsGetParticipantPermissions`. */
    fun membership(peer: TelegramPeer, user: TelegramPeer): ParticipantPermissions

    /** The admin rights [peer]'s participant listing carries for [user], or null when it holds none. */
    fun rights(peer: TelegramPeer, user: TelegramPeer): ChatPermissions?

    /** Grants or revokes [user]'s admin rights in [peer], which is `channelsEditAdmin`. */
    fun setAdmin(peer: TelegramPeer, user: TelegramPeer, permissions: ChatPermissions)

    /** Bans or restricts [user] in [peer], which is `channelsEditBanned`. */
    fun setBanned(peer: TelegramPeer, user: TelegramPeer, restrictions: ChatRestrictions)
}
