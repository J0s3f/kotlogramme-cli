package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Participant as FacadeParticipant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantMapperTest {
    @Test
    fun `maps a named member and keeps the role`() {
        val member = FacadeParticipant(
            user = user(id = 7, username = "ada", firstName = "Ada", lastName = "Lovelace"),
            role = "creator",
        ).toParticipant()

        assertEquals(7L, member.id)
        assertEquals("Ada Lovelace", member.displayName)
        assertEquals("ada", member.username)
        assertEquals("creator", member.role)
        assertTrue(member.isPrivileged)
    }

    @Test
    fun `an admin is privileged and falls back to the username for a name`() {
        val member = FacadeParticipant(
            user = user(id = 7, username = "ada"),
            role = "admin",
        ).toParticipant()

        assertEquals("ada", member.displayName)
        assertEquals("ada", member.username)
        assertEquals("admin", member.role)
        assertTrue(member.isPrivileged)
    }

    @Test
    fun `a plain member is not privileged and falls back to the id for a name`() {
        val member = FacadeParticipant(
            user = user(id = 7),
            role = "member",
        ).toParticipant()

        assertEquals("7", member.displayName)
        assertNull(member.username)
        assertEquals("member", member.role)
        assertFalse(member.isPrivileged)
    }

    @Test
    fun `a blank username is dropped rather than rendered blank`() {
        val member = FacadeParticipant(
            user = user(id = 7, firstName = "Ada", username = "  "),
            role = "member",
        ).toParticipant()

        assertEquals("Ada", member.displayName)
        assertNull(member.username)
    }
}
