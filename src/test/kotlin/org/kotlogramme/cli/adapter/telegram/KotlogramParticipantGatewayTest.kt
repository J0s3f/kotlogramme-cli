package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Dialog
import com.github.badoualy.telegram.api.Participant as FacadeParticipant
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramParticipantGatewayTest {
    @Test
    fun `participants resolves the chat and maps the page`() {
        val chatPeer = peer(id = -9, kind = "channel", name = "The Club", megagroup = true)
        val chatOperations = FakeParticipantChatOperations().apply { peers["club"] = chatPeer }
        val operations = FakeParticipantOperations().apply {
            page = listOf(
                FacadeParticipant(user = user(id = 1, username = "ada", firstName = "Ada"), role = "creator"),
                FacadeParticipant(user = user(id = 2, firstName = "Grace"), role = "member"),
            )
        }

        val members = KotlogramParticipantGateway(operations, ChatReferenceResolver(chatOperations))
            .participants("@club", limit = 50)

        assertEquals(listOf("club"), chatOperations.resolvedUsernames)
        assertEquals(listOf(ParticipantCall(chatPeer, 50)), operations.calls)
        assertEquals(listOf(1L, 2L), members.map { it.id })
        assertEquals(listOf("Ada", "Grace"), members.map { it.displayName })
        assertEquals(listOf("creator", "member"), members.map { it.role })
    }

    @Test
    fun `kick resolves both the chat and the user before removing them`() {
        val chatPeer = peer(id = -9, kind = "channel", name = "The Club", megagroup = true)
        val userPeer = peer(id = 1, kind = "user", username = "ada", name = "Ada")
        val chatOperations = FakeParticipantChatOperations().apply {
            peers["club"] = chatPeer
            peers["ada"] = userPeer
        }
        val operations = FakeParticipantOperations()

        KotlogramParticipantGateway(operations, ChatReferenceResolver(chatOperations)).kick("@club", "@ada")

        assertEquals(listOf("club", "ada"), chatOperations.resolvedUsernames)
        assertEquals(listOf(KickCall(chatPeer, userPeer)), operations.kicks)
    }

    @Test
    fun `invite resolves both the chat and the user before adding them`() {
        val chatPeer = peer(id = -9, kind = "channel", name = "The Club", megagroup = true)
        val userPeer = peer(id = 1, kind = "user", username = "ada", name = "Ada")
        val chatOperations = FakeParticipantChatOperations().apply {
            peers["club"] = chatPeer
            peers["ada"] = userPeer
        }
        val operations = FakeParticipantOperations()

        KotlogramParticipantGateway(operations, ChatReferenceResolver(chatOperations)).invite("@club", "@ada")

        assertEquals(listOf("club", "ada"), chatOperations.resolvedUsernames)
        assertEquals(listOf(InviteCall(chatPeer, userPeer)), operations.invites)
    }
}

internal data class ParticipantCall(val peer: TelegramPeer, val limit: Int)

internal data class KickCall(val peer: TelegramPeer, val user: TelegramPeer)

internal data class InviteCall(val peer: TelegramPeer, val user: TelegramPeer)

internal class FakeParticipantOperations : FacadeParticipantOperations {
    var page: List<FacadeParticipant> = emptyList()
    val calls = mutableListOf<ParticipantCall>()
    val kicks = mutableListOf<KickCall>()
    val invites = mutableListOf<InviteCall>()

    override fun participants(peer: TelegramPeer, limit: Int): List<FacadeParticipant> {
        calls += ParticipantCall(peer, limit)
        return page
    }

    override fun invite(peer: TelegramPeer, user: TelegramPeer) {
        invites += InviteCall(peer, user)
    }

    override fun kick(peer: TelegramPeer, user: TelegramPeer) {
        kicks += KickCall(peer, user)
    }
}

/** A chat seam that hands back a different peer per username, so both resolutions are observable. */
internal class FakeParticipantChatOperations : FacadeChatOperations {
    val peers = mutableMapOf<String, TelegramPeer>()
    val resolvedUsernames = mutableListOf<String>()

    override fun dialogs(limit: Int): List<Dialog> = emptyList()

    override fun resolveUsername(username: String): TelegramPeer {
        resolvedUsernames += username
        return peers[username] ?: error("no peer for $username")
    }

    override fun parseInviteLink(link: String): String? = null

    override fun importChatInvite(link: String): TelegramPeer? = null
}
