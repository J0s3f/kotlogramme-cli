package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions as FacadePermissions
import com.github.badoualy.telegram.api.ChatRestrictions as FacadeRestrictions
import com.github.badoualy.telegram.api.ParticipantPermissions as FacadeMembership
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminRightsMapperTest {
    @Test
    fun `admin rights round-trip through the facade permissions`() {
        val rights = ChatRights(
            changeInfo = true,
            postMessages = false,
            editMessages = true,
            deleteMessages = false,
            banUsers = true,
            inviteUsers = false,
            pinMessages = true,
            addAdmins = false,
            anonymous = true,
            manageCall = false,
        )

        assertEquals(rights, rights.toFacadePermissions().toChatRights())
    }

    @Test
    fun `facade permissions map every flag`() {
        val facade = FacadePermissions(
            changeInfo = true,
            postMessages = true,
            editMessages = true,
            deleteMessages = true,
            banUsers = true,
            inviteUsers = true,
            pinMessages = true,
            addAdmins = true,
            anonymous = true,
            manageCall = true,
        )

        assertEquals(ChatRights.ALL, facade.toChatRights())
    }

    @Test
    fun `role membership projects a creator as every right`() {
        assertEquals(ChatRights.ALL, FacadeMembership(true, true, false, false, false, true).toChatRights())
    }

    @Test
    fun `role membership projects an ordinary member as no rights`() {
        assertEquals(ChatRights.NONE, FacadeMembership(false, false, false, false, true, false).toChatRights())
    }

    @Test
    fun `an admin keeps the granular add-admins flag`() {
        val projected = FacadeMembership(false, true, false, false, false, false).toChatRights()

        assertEquals(ChatRights.ALL.copy(addAdmins = false), projected)
    }

    @Test
    fun `restrictions round-trip with an expiry`() {
        val restrictions = ChatRestrictions(
            sendMessages = false,
            untilDate = Instant.parse("2026-01-02T03:04:05Z"),
        )

        assertEquals(restrictions, restrictions.toFacadeRestrictions().toDomainRestrictions())
    }

    @Test
    fun `an absent expiry becomes the epoch and back to forever`() {
        val forever = ChatRestrictions.NONE_ALLOWED

        val facade = forever.toFacadeRestrictions()

        assertEquals(NEVER_EXPIRES, facade.untilDate)
        assertEquals(null, facade.toDomainRestrictions().untilDate)
    }

    @Test
    fun `the facade epoch reads back as forever`() {
        val facade = FacadeRestrictions(
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
            untilDate = NEVER_EXPIRES,
        )

        assertEquals(null, facade.toDomainRestrictions().untilDate)
    }
}
