package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Participant
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade participant calls the participant gateway needs, narrowed to a seam a test can
 * implement.
 *
 * This exists so [KotlogramParticipantGateway] can be exercised without a live client. Reference
 * resolution is left to the gateway, which shares the adapter's [ChatReferenceResolver]; these
 * methods take facade peers only.
 */
internal interface FacadeParticipantOperations {
    /** Lists up to [limit] members of [peer], which is `channelsGetParticipants`. */
    fun participants(peer: TelegramPeer, limit: Int): List<Participant>

    /** Removes [user] from [peer], which is `channelsKickParticipant`. */
    fun kick(peer: TelegramPeer, user: TelegramPeer)

    /** Adds [user] to [peer], which is `channelsInviteToChannel`. */
    fun invite(peer: TelegramPeer, user: TelegramPeer)
}
