package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions
import com.github.badoualy.telegram.api.ChatRestrictions
import com.github.badoualy.telegram.api.ParticipantPermissions
import com.github.badoualy.telegram.api.TelegramPeer
import org.kotlogramme.cli.domain.ChatRights
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramAdminRightsGatewayTest {
    private val club = peer(id = -100, kind = "channel", name = "The Club", megagroup = true)
    private val ada = peer(id = 7, kind = "user", username = "ada", name = "Ada")
    private val chatOperations = FakeParticipantChatOperations().apply {
        peers["club"] = club
        peers["ada"] = ada
    }

    @Test
    fun `permissions maps the granular rights the listing carries`() {
        val operations = FakeAdminOperations().apply {
            membership = ParticipantPermissions(
                isCreator = false,
                isAdmin = true,
                isBanned = false,
                hasLeft = false,
                hasDefaultPermissions = false,
                canAddAdmins = true,
            )
            rights = ChatPermissions(
                changeInfo = true,
                postMessages = false,
                editMessages = true,
                deleteMessages = false,
                banUsers = true,
                inviteUsers = false,
                pinMessages = true,
                addAdmins = true,
                anonymous = false,
                manageCall = false,
            )
        }

        val rights = gatewayWith(operations).permissions("@club", "@ada")

        assertEquals(listOf("club", "ada"), chatOperations.resolvedUsernames)
        assertEquals(listOf(MembershipCall(club, ada)), operations.memberships)
        assertEquals(listOf(RightsCall(club, ada)), operations.listedRights)
        assertEquals(
            ChatRights(
                changeInfo = true,
                editMessages = true,
                banUsers = true,
                pinMessages = true,
                addAdmins = true,
            ),
            rights,
        )
    }

    @Test
    fun `an ordinary member holds no rights and the listing is never read`() {
        val operations = FakeAdminOperations()

        val rights = gatewayWith(operations).permissions("@club", "@ada")

        assertEquals(ChatRights.NONE, rights)
        assertEquals(listOf(MembershipCall(club, ada)), operations.memberships)
        assertEquals(emptyList(), operations.listedRights)
    }

    @Test
    fun `an admin missing from the listing falls back to the role`() {
        val operations = FakeAdminOperations().apply {
            membership = ParticipantPermissions(
                isCreator = false,
                isAdmin = true,
                isBanned = false,
                hasLeft = false,
                hasDefaultPermissions = false,
                canAddAdmins = false,
            )
            rights = null
        }

        val rights = gatewayWith(operations).permissions("@club", "@ada")

        assertEquals(ChatRights.ALL.copy(addAdmins = false), rights)
        assertEquals(listOf(RightsCall(club, ada)), operations.listedRights)
    }

    @Test
    fun `setAdmin resolves both peers and maps the rights`() {
        val operations = FakeAdminOperations()

        gatewayWith(operations).setAdmin("@club", "@ada", ChatRights(changeInfo = true, addAdmins = true))

        assertEquals(listOf("club", "ada"), chatOperations.resolvedUsernames)
        assertEquals(
            listOf(
                SetAdminCall(
                    club,
                    ada,
                    ChatPermissions(
                        changeInfo = true,
                        postMessages = false,
                        editMessages = false,
                        deleteMessages = false,
                        banUsers = false,
                        inviteUsers = false,
                        pinMessages = false,
                        addAdmins = true,
                        anonymous = false,
                        manageCall = false,
                    ),
                ),
            ),
            operations.setAdmins,
        )
    }

    @Test
    fun `setBanned resolves both peers and maps the restrictions`() {
        val operations = FakeAdminOperations()
        val restrictions = org.kotlogramme.cli.domain.ChatRestrictions.NONE_ALLOWED
            .copy(untilDate = Instant.ofEpochMilli(1_000))

        gatewayWith(operations).setBanned("@club", "@ada", restrictions)

        assertEquals(listOf("club", "ada"), chatOperations.resolvedUsernames)
        assertEquals(
            listOf(
                SetBannedCall(
                    club,
                    ada,
                    ChatRestrictions(
                        viewMessages = false,
                        sendMessages = false,
                        sendMedia = false,
                        sendStickers = false,
                        sendGifs = false,
                        sendGames = false,
                        sendInline = false,
                        embedLinks = false,
                        sendPolls = false,
                        changeInfo = false,
                        inviteUsers = false,
                        pinMessages = false,
                        untilDate = 1_000,
                    ),
                ),
            ),
            operations.setBanneds,
        )
    }

    private fun gatewayWith(operations: FakeAdminOperations): KotlogramAdminRightsGateway =
        KotlogramAdminRightsGateway(operations, ChatReferenceResolver(chatOperations))
}

internal data class MembershipCall(val peer: TelegramPeer, val user: TelegramPeer)

internal data class RightsCall(val peer: TelegramPeer, val user: TelegramPeer)

internal data class SetAdminCall(
    val peer: TelegramPeer,
    val user: TelegramPeer,
    val permissions: ChatPermissions,
)

internal data class SetBannedCall(
    val peer: TelegramPeer,
    val user: TelegramPeer,
    val restrictions: ChatRestrictions,
)

/** A [FacadeAdminOperations] that records every call and returns canned membership and rights. */
internal class FakeAdminOperations : FacadeAdminOperations {
    var membership: ParticipantPermissions = ParticipantPermissions(
        isCreator = false,
        isAdmin = false,
        isBanned = false,
        hasLeft = false,
        hasDefaultPermissions = true,
        canAddAdmins = false,
    )
    var rights: ChatPermissions? = null

    val memberships = mutableListOf<MembershipCall>()
    val listedRights = mutableListOf<RightsCall>()
    val setAdmins = mutableListOf<SetAdminCall>()
    val setBanneds = mutableListOf<SetBannedCall>()

    override fun membership(peer: TelegramPeer, user: TelegramPeer): ParticipantPermissions {
        memberships += MembershipCall(peer, user)
        return membership
    }

    override fun rights(peer: TelegramPeer, user: TelegramPeer): ChatPermissions? {
        listedRights += RightsCall(peer, user)
        return rights
    }

    override fun setAdmin(peer: TelegramPeer, user: TelegramPeer, permissions: ChatPermissions) {
        setAdmins += SetAdminCall(peer, user, permissions)
    }

    override fun setBanned(peer: TelegramPeer, user: TelegramPeer, restrictions: ChatRestrictions) {
        setBanneds += SetBannedCall(peer, user, restrictions)
    }
}
