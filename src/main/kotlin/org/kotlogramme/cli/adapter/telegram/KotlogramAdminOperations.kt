package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions
import com.github.badoualy.telegram.api.ChatRestrictions
import com.github.badoualy.telegram.api.ParticipantPermissions
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The real [FacadeAdminOperations], delegating straight to the facade client.
 *
 * The granular rights read scans the participant listing, so it is bounded by a fixed limit rather
 * than paging the whole chat; a member beyond that limit falls back to the role check alone.
 */
internal class KotlogramAdminOperations(private val client: TelegramClient) : FacadeAdminOperations {
    override fun membership(peer: TelegramPeer, user: TelegramPeer): ParticipantPermissions =
        client.channelsGetParticipantPermissions(peer, user)

    override fun rights(peer: TelegramPeer, user: TelegramPeer): ChatPermissions? =
        client.channelsGetParticipants(peer, RIGHTS_LOOKUP_LIMIT)
            .firstOrNull { it.user.id == user.id }
            ?.permissions

    override fun setAdmin(peer: TelegramPeer, user: TelegramPeer, permissions: ChatPermissions) =
        client.channelsEditAdmin(peer, user, permissions)

    override fun setBanned(peer: TelegramPeer, user: TelegramPeer, restrictions: ChatRestrictions) =
        client.channelsEditBanned(peer, user, restrictions)

    private companion object {
        /** How many participants a single-member rights lookup scans before giving up. */
        const val RIGHTS_LOOKUP_LIMIT = 200
    }
}
