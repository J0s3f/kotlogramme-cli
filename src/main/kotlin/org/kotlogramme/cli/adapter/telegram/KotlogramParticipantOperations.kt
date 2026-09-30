package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Participant
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeParticipantOperations], delegating straight to the facade client. */
internal class KotlogramParticipantOperations(private val client: TelegramClient) : FacadeParticipantOperations {
    override fun participants(peer: TelegramPeer, limit: Int): List<Participant> =
        client.channelsGetParticipants(peer, limit)

    override fun kick(peer: TelegramPeer, user: TelegramPeer) = client.channelsKickParticipant(peer, user)
}
